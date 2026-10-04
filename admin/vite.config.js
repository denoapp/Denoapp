import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
// The Pages workflow uploads `admin/dist` as the site artifact, so the built
// files become the site ROOT. A sub-path base would point every asset at a
// directory that does not exist on the published site.
export default defineConfig({
  plugins: [react()],
  base: '/',
})
