const express = require('express');
const cors = require('cors');

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json({ limit: '1mb' }));

const regionMap = new Map();

app.get('/api/roblox-region', (req, res) => {
  const jobId = String(req.query.jobId || '').trim();

  if (!jobId) {
    return res.status(400).json({ error: 'jobId is required' });
  }

  const region = regionMap.get(jobId);
  if (!region) {
    return res.status(404).json({ error: 'region not found for jobId' });
  }

  return res.json({ region });
});

app.post('/api/roblox-region', (req, res) => {
  const jobId = String(req.body?.jobId || '').trim();
  const region = String(req.body?.region || '').trim();

  if (!jobId || !region) {
    return res.status(400).json({ error: 'jobId and region are required' });
  }

  regionMap.set(jobId, region);
  return res.json({ ok: true, jobId, region });
});

app.listen(port, () => {
  console.log(`Region backend listening on http://localhost:${port}`);
});
