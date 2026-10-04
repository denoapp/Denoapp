import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
// The panel is served as a project page, so every asset URL has to carry the
// repository name. With a base of "/" the built index.html asks for
// /assets/... on a site that only answers under /Denoapp/, and the app boots to
// a blank page because the module never loads.
export default defineConfig({
  plugins: [react()],
  base: '/Denoapp/',
})
