# kotlinx-serialization keeps its serializers through the generated companions;
# Room and Retrofit models are held by :core:data's consumer rules.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
