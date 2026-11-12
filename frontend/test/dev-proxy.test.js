import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer as createHttpServer } from 'node:http'
import { createServer, loadConfigFromFile } from 'vite'

test('development proxy forwards API requests without the /api prefix', async () => {
  const backend = createHttpServer((request, response) => {
    response.end(request.url)
  })
  await new Promise((resolve) => backend.listen(0, '127.0.0.1', resolve))
  const loaded = await loadConfigFromFile({ command: 'serve', mode: 'development' }, 'vite.config.ts')
  const proxy = loaded.config.server.proxy['/api']
  proxy.target = `http://127.0.0.1:${backend.address().port}`
  const server = await createServer({ ...loaded.config, configFile: false, optimizeDeps: { noDiscovery: true }, server: { ...loaded.config.server, port: 0 } })
  try {
    await server.listen()
    const port = server.httpServer.address().port
    const response = await fetch(`http://localhost:${port}/api/scans?page=2`)
    assert.equal(await response.text(), '/scans?page=2')
  } finally {
    await server.close()
    await new Promise((resolve) => backend.close(resolve))
  }
})
