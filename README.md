Zinc 1.0 in sbt 0.13.13
====

This is the repo where lives Zinc and the sbt 0.13.13 plugin to try it out.

For information on Zinc, please [head to the official repo](https://github.com/sbt/zinc).
Here, we mainly document the experimental support of Zinc 1.0 in sbt 0.13.13.

## Installation

```scala
resolvers += Resolver.bintrayIvyRepo("scalacenter", "sbt-releases")
addSbtPlugin("org.scala-sbt" % "sbt-zinc-plugin" % "1.0.0-X10")
```

## Update

Every time a new Zinc version is out, you need to update this plugin. This plugin
relies on a mega jar of the so-called Zinc proxy to classload it into sbt, and
therefore the Zinc version is hardcoded into the code.

## Use

To compile your project with Zinc 1.0, run `zincCompile`.

In complex setups, there may be some problems with caching. In the next days,
we will improve these situations.

## Benchmark: compare numbers

The current status of the plugin is precarious and it only supports `compile`.
This means that it reuses the current sbt compiler for several tasks, like
compiling dependent subprojects before executing `zincCompile`.

However, there are some tips that you need to know in order to compare numbers
between Zinc 1.0 and Zinc 0.13.13:
  
* Make sure that you have not compiled anything before in the same session,
  otherwise the ordinary Zinc 0.13.x compiler will be hot. If Zinc 1.0 is not
  hot, you'll be comparing pears with apples: results will vary a lot and will
  not be representative.
* If you want to benchmark a project, make sure that you compile the subprojects
  before and that you close and start sbt again. Next time you start sbt, the
  sbt cached compilers will be cold in the same way the Zinc 1.0 will.
  
# File bugs

One of the reasons why we release this plugin is to battle-test Zinc 1.0 before
a stable release. Please, help us improve current Zinc by mixing well-documented
issues in the official [Zinc repository](https://github.com/sbt/zinc). If you
happen to have a problem with this plugin, file issues here.

Before filing an issue, make sure that you enable the `zincDebug` setting
in your project with:
```scala
zincDebug := true
```
  
When you do it, you'll start to see a lot of information about the analysis done
by Zinc, the classpath used, the Scala options, etc. Please, include this information
in the issue description.
