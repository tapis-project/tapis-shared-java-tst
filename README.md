#Tapis Shared Java 


### Profiles

There is a profile with ID `ossrh` in the tapis-bom. This is the profile that is needed to deploy/release as it activates 
the plugins that are used for signing/javadocs/sourcemaps etc. 

### Deploy / Release
A *deployment* pushes a new snapshot build to maven central

    mvn clean deploy -P ossrh

A *release* pushes a new build to maven central and is available for all to download.  
    
    mvn clean release:prepare release:perform -P ossrh

A typical workflow is to deploy snapshots continually, then when features are tested and stabilized, push a new
release based on the latest snapshot. 