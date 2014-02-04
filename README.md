# feedzai Open Scoring Server (FOS) - R m Scoring and Training module

fos-r is a [fos-core] [R] implementation

[![Build Status](https://feedzaios.ci.cloudbees.com/buildStatus/icon?job=fos-r)](https://feedzaios.ci.cloudbees.com/job/fos-r/)

[![CloudbeesDevCloud](http://www.cloudbees.com/sites/default/files/Button-Built-on-CB-1.png)](http://www.cloudbees.com/dev)

## Why FOS

There are pretty good machine learning training and scoring frameworks/libraries out there, but they don't provide the
following benefits:

1. Common API: fos provides a common abstraction for model attributes, model training and model scoring. Using a [Weka]
based classifier will use have exactly the same API as using a R based classifier.
1. Scoring & Training as a remote service: Training and scoring can be farmed to dedicated servers in the network
enabling both vertical and horizontal scaling.
1. Import and Export models: A model could be trained in a development box and imported seamlessly into a remote server
1. Scalable and low latency scoring: Marshalling and Unmarshalling scoring requests/responses can be responsible
for a significant amount of overhead. Along with the slow RMI based interface, fos also supports scoring using [Kryo].

## Compiling fos-r

You need:

1. [Java SDK]: Java 7
1. [Maven]: Tested with maven 3.0.X
1. [fos-core]
1. [R]: Tested against R 2.15 on Linux (Tested on debian, centos, ubuntu)
1. Access to maven central repo (or a local proxy)


After installing [R] you need to install R RServe library

To install Rserve open a command line, start R and type the following command:

 ```R
 install.packages("Rserve")
 ```

fos-r provides a built-in training module using [randomForest]. In order to use it
you need to install the following R packages:

```R
install.packages("randomForest")
install.packages("doMC")
install.packages("foreach")
install.packages("foreign")
install.packages("e1071")
install.packages("kernlab")
```

After Rserve has been installed successfully, start a rserve daemon:

```Shell
R --no-save --slave -e "library(Rserve);Rserve(args='--no-save --slave');"
```

After [Java SDK] and [Maven] have been installed run the following command

```Shell
mvn clean install
```

This should compile fos-r, ran all the tests and install all modules into your local maven repo.

## Running FOS-R 

The default FOS-R package uses [fos-weka]. You need to set `fos.factoryName` in fos.properties like this:

```
fos.factoryName=com.feedzai.fos.impl.r.RManagerFactory
```

[Kryo]: https://github.com/EsotericSoftware/kryo
[fos-r]: https://github.com/feedzai/fos-r
[fos-core]: https://github.com/feedzai/fos-core
[fos-weka]: https://github.com/feedzai/fos-weka
[Weka]: http://www.cs.waikato.ac.nz/ml/weka/
[R]: http://www.r-project.org/
[Maven]: http://maven.apache.org/
[Java SDK]: http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
[randomForest]: http://cran.r-project.org/web/packages/randomForest/index.html




