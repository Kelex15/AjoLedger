import path from 'node:path'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/** Vercel's cleanUrls serves /engineering from engineering.html. Match that locally so links don't 404 in dev. */
function cleanUrls(): Plugin {
  return {
    name: 'clean-urls',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        if (req.url === '/engineering') req.url = '/engineering.html'
        next()
      })
    },
  }
}

export default defineConfig({
  plugins: [react(), tailwindcss(), cleanUrls()],
  build: {
    // Two entry points rather than a router, so each page ships its own <head>
    // and its own link-preview tags as static HTML.
    rollupOptions: {
      input: {
        index: path.resolve(import.meta.dirname, 'index.html'),
        engineering: path.resolve(import.meta.dirname, 'engineering.html'),
      },
    },
  },
  server: {
    port: 3001,
  },
})
