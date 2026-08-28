/**
 * Tombstone service worker.
 *
 * The old NizKarya PWA registered a caching service worker at this path.
 * Deleting the file would not remove it: browsers keep the last installed
 * worker and it would go on serving the dead app shell from cache forever.
 *
 * This replaces it, drops every cache it created, unregisters itself, and
 * reloads any open tab so the visitor lands on the current page.
 */
self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const names = await caches.keys();
      await Promise.all(names.map((name) => caches.delete(name)));
      await self.registration.unregister();

      const clients = await self.clients.matchAll({ type: "window" });
      for (const client of clients) {
        client.navigate(client.url);
      }
    })()
  );
});
