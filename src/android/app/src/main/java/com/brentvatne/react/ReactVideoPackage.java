// src/main/java/com/brentvatne/react/ReactVideoPackage.java
package com.brentvatne.react;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;

import java.util.Collections;
import java.util.List;

public class ReactVideoPackage implements com.facebook.react.ReactPackage {
    @Override public List<NativeModule> createNativeModules(ReactApplicationContext ctx) { return Collections.emptyList(); }
    @Override public List<com.facebook.react.uimanager.ViewManager> createViewManagers(ReactApplicationContext ctx) { return Collections.emptyList(); }
}
