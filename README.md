Android Signature Pad
====================

Android Signature Pad is an Android library for drawing smooth signatures. It uses variable width Bézier curve interpolation based on [Smoother Signatures](http://corner.squareup.com/2012/07/smoother-signatures.html) post by [Square](https://squareup.com).

![Screenshot](https://github.com/gcacace/android-signaturepad/raw/master/header.png)

## Features
 * Bézier implementation for a smoother line
 * Variable point size based on velocity
 * Customizable pen color and size
 * Bitmap and SVG support
 * Data Binding

## Installation

Latest version of the library can be found on Maven Central.

### For Gradle users

Open your `build.gradle` and make sure that Maven Central repository is declared into `repositories` section:
```gradle
   repositories {
       mavenCentral()
   }
```
Then, include the library as dependency:
```gradle
compile 'com.github.gcacace:signature-pad:1.3.1'
```

### For Maven users

Add this dependency to your `pom.xml`:
```xml
<dependency>
  <groupId>com.github.gcacace</groupId>
  <artifactId>signature-pad</artifactId>
  <version>1.3.1</version>
  <type>aar</type>
</dependency>
```

## Usage

*Please see the `/SignaturePad-Example` app for a more detailed code example of how to use the library.*

1. Add the `SignaturePad` view to the layout you want to show.
```xml
 <com.github.gcacace.signaturepad.views.SignaturePad
     xmlns:android="http://schemas.android.com/apk/res/android"
     xmlns:app="http://schemas.android.com/apk/res-auto"
     android:id="@+id/signature_pad"
     android:layout_width="match_parent"
     android:layout_height="match_parent"
     app:penColor="@android:color/black"
     />
```

2. Configure attributes.
 * `penMinWidth` - The minimum width of the stroke (default: 3dp).
 * `penMaxWidth` - The maximum width of the stroke (default: 7dp).
 * `penColor` - The color of the stroke (default: Color.BLACK).
 * `velocityFilterWeight` - Weight used to modify new velocity based on the previous velocity (default: 0.9).
 * `clearOnDoubleClick` - Double click to clear pad (default: false)

3. Configure signature events listener

 An `OnSignedListener` can be set on the view:
 ```java

 mSignaturePad = (SignaturePad) findViewById(R.id.signature_pad);
 mSignaturePad.setOnSignedListener(new SignaturePad.OnSignedListener() {

      @Override
      public void onStartSigning() {
          //Event triggered when the pad is touched
      }

     @Override
     public void onSigned() {
         //Event triggered when the pad is signed
     }

     @Override
     public void onClear() {
         //Event triggered when the pad is cleared
     }
 });
 ```

4. Get signature data
 * `getSignatureBitmap()` - A signature bitmap with a white background.
 * `getTransparentSignatureBitmap()` - A signature bitmap with a transparent background.
 * `getSignatureSvg()` - A signature Scalable Vector Graphics document.

## Data Binding

The `SignaturePad` view has custom Data Binding attribute setters for all the listener events:

```xml
 <com.github.gcacace.signaturepad.views.SignaturePad
     xmlns:android="http://schemas.android.com/apk/res/android"
     xmlns:bind="http://schemas.android.com/apk/res-auto"
     android:id="@+id/signature_pad"
     android:layout_width="match_parent"
     android:layout_height="match_parent"
     bind:onStartSigning="@{activity.onStartSigning}"
     bind:onSigned="@{activity.onSigned}"
     bind:onClear="@{activity.onClear}" />
```

## Cordova Plugin

Thanks to [netinhoteixeira](https://github.com/netinhoteixeira/), there is a Cordova plugin using that library.
Please refer to https://github.com/netinhoteixeira/cordova-plugin-signature-view.

## NativeScript Plugin
Thanks to [bradmartin](https://github.com/bradmartin), there is a NativeScript plugin.
Please refer to [https://github.com/bradmartin/nativescript-signaturepad](https://github.com/bradmartin/nativescript-signaturepad).

## Caveats

Currently doesn't support screen rotations. Pull requests are welcome!

## License

    Copyright 2014-2016 Gianluca Cacace

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
