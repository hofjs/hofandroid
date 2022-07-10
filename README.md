# Hof.js android

**Hof.js android** is a **modern library** for the development of Hof.js based Android apps and is a part of the Hof.js project. It is an **open source project of Hof University of Applied Sciences** and **was created by Prof. Dr. Walter Kern**.

**Hof.js android provides an Activity base class that renders any Hof.js-based application deployed to the app assets folder.** Additionally, helper classes for common use cases are provided.

Contact us if you are a student of Hof University of Applied Sciences and would like to contribute.

## Contact
* Organization: https://www.hof-university.de
* Mail: hofjs@hof-university.de
* Impressum / Imprint: https://www.hof-university.de/impressum.html

## Key features
This framework has the following advantages, among others:
* **Extremely simple packaging** of Hof.js apps, because you can simply copy the Hof.js app folder to the app assets folder.
* **Good render performance** because using modern Android apis to call local resources means no local web server is needed, so the corresponding overhead is eliminated.
* **Fetch API accesses to any web page work**, giving hosted Hof.js apps the same capabilities as native Android apps, and without plugins or special APIs on the client..
* **Useful helper libraries** support periodic updates of app content.

## Introductory examples

**Simple loading of a Hof.js app**

The following sample shows the minimal code required to render a Hof.js app as Android app.

```kotlin
package de.sample.hofjsapp

import android.os.Bundle

class MainActivity : PwaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // All that is required to load startpage of hof.js app
        // (file has to be placed in src/main/assets)
        setContentUrl("index.html") 
    }
}
```

**Loading of a Hof.js app and exposing functions to Hof.js app javascript code**

The following sample shows a feature to support Android activity functions to be called from the JavaScript code of the provided Hof.js app.

```kotlin
package de.sample.hofjsapp

import android.os.Bundle

class MainActivity : PwaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // All that is required to load startpage of hof.js app
        // (file has to be placed in src/main/assets)
        setContentUrl("index.html", "pwaActivity") 
    }

    @JavascriptInterface
    fun sayHello(name: String) {
        // This method can be called from JavaScript of Hof.js app
        // by using pwaActivity.sayHello("World")
    }
}
```

## Documentation

Currently there is no special documentation, because the usage as shown in the example above is very simple.

You can contribute by sending pull requests to [this repository](https://github.com/hofjs/hofandroid).


## License

Hof.js android is [MIT licensed](./LICENSE.md).