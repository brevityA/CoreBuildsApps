// Optional art-director configuration. Keep the CORS proxy out of the render
// path: the studio is GPU-local and the existing Core Builds worker is scoped to
// AIOStreams hosts, not arbitrary image URLs.
window.CORE_MOTION_STUDIO = Object.freeze({
  catalog: '../../motion-engine/prequels.json',
  rawBase: 'https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/',
  sourceManifest: '../../Wallpapers/manifest.json',
  corsProxy: 'https://core-builds-cors-proxy.tlorenzato26.workers.dev'
});
