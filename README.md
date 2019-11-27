# JANT

If you're like me, you hate working with the verbosity of XML.  This project leverages the power of ant, but swaps out the verbose XML configuration with JSON configuration.

## Developing

First, grab an existing version of ant and ensure it is on the PATH.

Then, run this command to grab the jars:

```  
ant -f fetch.xml all -Ddest=optional
```

Then import as an existing project into Eclipse!