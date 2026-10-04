# kotlinx.serialization: keep generated serializers of @Serializable classes (nav keys, stored fields).
-keepclassmembers @kotlinx.serialization.Serializable class dev.glowcow.stackd.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit instantiates the component registrars listed in the manifest via reflection.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
