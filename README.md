MapLibre Navigation SDK for Android (and KMP)
=============================================

This is a fork of [MapLibre Navigation SDK for Android](https://github.com/maplibre/maplibre-navigation-android).

## Getting started

### Core

`maplibre-navigation-core` has been untouched, you can grab this from maven repo.
Or just take the dir from this repo

### NavigationUi

Add `libandroid-navigation-ui/` to your project files and link it in your `build.gradle`.

### Config

Edit `app/src/main/res/values/developer-config.xml` to add:
- Your valhalla server address.
- Your tileserver style address.
- `<string name="routing_url" translatable="false">your_routing_url</string>`

And set the mapbox_access_token:

```xml
<string name="mapbox_access_token" translatable="false">tk.no_token</string>
```

Inside `libandroid-navigation-ui/src/main/res/values/config.xml`, place the same values as above.

### Usage of demo

Before building, check that `readCSVToFeatureCollection` has path to csv data in assets dir.
