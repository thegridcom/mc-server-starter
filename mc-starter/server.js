require('dotenv').config();
const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 9009;
let PELICAN_URL = (process.env.PELICAN_URL || '').trim().replace(/\/$/, '');
if (PELICAN_URL && !/^https?:\/\//i.test(PELICAN_URL)) {
  PELICAN_URL = 'https://' + PELICAN_URL;
}
const SERVER_ID = process.env.SERVER_ID;
const API_KEY = process.env.PELICAN_API_KEY;
const COOLDOWN_MS = (parseInt(process.env.COOLDOWN_SECONDS, 10) || 45) * 1000;

let lastStartAttempt = 0;

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Server-Status vom Panel abfragen
app.get('/api/status', async (req, res) => {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 4000);

  try {
    const response = await fetch(`${PELICAN_URL}/api/client/servers/${SERVER_ID}/resources`, {
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
        'Accept': 'application/vnd.pelican.v1+json'
      },
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (!response.ok) {
      const errText = await response.text();
      let errorDetail = errText;
      try {
        const parsed = JSON.parse(errText);
        errorDetail = parsed.errors?.[0]?.detail || parsed.errors?.[0]?.code || errText;
      } catch {
        errorDetail = errText.slice(0, 150);
      }
      console.error(`[Pelican Status Fehler] Status ${response.status}: ${errorDetail}`);
      return res.json({ state: 'unknown', error: errorDetail });
    }

    const data = await response.json();
    return res.json({ state: data.attributes?.current_state || 'offline' });
  } catch (err) {
    clearTimeout(timeout);
    return res.json({ state: 'offline' });
  }
});

// Start-Befehl ausführen
app.post('/api/start', async (req, res) => {
  const now = Date.now();
  const diff = now - lastStartAttempt;

  if (diff < COOLDOWN_MS) {
    const remaining = Math.ceil((COOLDOWN_MS - diff) / 1000);
    return res.status(429).json({
      success: false,
      message: `Bitte warte noch ${remaining}s, bevor du erneut klickst.`
    });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 6000);

  try {
    const response = await fetch(`${PELICAN_URL}/api/client/servers/${SERVER_ID}/power`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
        'Content-Type': 'application/json',
        'Accept': 'application/vnd.pelican.v1+json'
      },
      body: JSON.stringify({ signal: 'start' }),
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (response.status === 204) {
      lastStartAttempt = now;
      return res.json({
        success: true,
        message: 'Startbefehl gesendet! Der Server fährt jetzt hoch.'
      });
    }

    const errBody = await response.text();
    let errorDetail = errBody;
    try {
      const parsed = JSON.parse(errBody);
      errorDetail = parsed.errors?.[0]?.detail || parsed.errors?.[0]?.code || parsed.message || errBody;
    } catch {
      errorDetail = errBody.slice(0, 300);
    }
    console.error(`[Pelican Fehler] Status ${response.status}: ${errorDetail}`);
    return res.status(502).json({
      success: false,
      message: `Pelican Panel Fehler (${response.status}): ${errorDetail}`
    });

  } catch (err) {
    clearTimeout(timeout);
    const msg = err.name === 'AbortError'
      ? 'Timeout: Das Panel antwortet nicht rechtzeitig.'
      : 'Verbindungsfehler zum Panel.';
    return res.status(504).json({ success: false, message: msg });
  }
});

process.on('uncaughtException', (err) => {
  console.error('[MC-Starter] Uncaught Exception:', err);
});
process.on('unhandledRejection', (reason, promise) => {
  console.error('[MC-Starter] Unhandled Rejection at:', promise, 'reason:', reason);
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[MC-Starter] Webdienst läuft auf Port ${PORT} (0.0.0.0)`);
});

