# Tizen SDK Build Tool

This simple program will allow you to quickly build Tizen App Packages (.wgt) for convenience.

This program may be compiled into a JAR and used directly in your IDE of choice. For example, as a custom Builder in Eclipse.

When using this as a JAR Builder, these arguments might be useful as a template when targetting your java executable:

```
-jar ${workspace_loc:/ProjectName/sdkb.jar}
-i D:\Repos\TizenProject
-w D:\Workspaces\Tizen
-t D:\tizen-sdk\tools\ide\bin\tizen.bat
-o
```

Uses libraries:

* args4j - Parse command line arguments
* Apache Commons IO - Trusted efficient File copy methods
* YUI Compressor - Optional script compression / obfuscation
