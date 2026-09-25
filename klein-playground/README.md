# Klein playground

A browser playground for Klein contracts, rules and runs. It runs entirely in the browser on
`klein-js`, Klein's JavaScript binding.

## Running it

The app imports the binding from its Gradle build output, so build that first:

```bash
npm run binding    # runs ./gradlew :klein-js:jsBrowserProductionLibraryDistribution
npm install
npm run dev
```

Rebuild the binding after changing `klein-js` or `klein-lib`; Vite picks up the new output on reload.

`npm run check` type-checks the app, and `npm run build` writes a static site to `dist/`.
