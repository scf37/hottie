addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.10")

resolvers += Resolver.url("plugins", url("https://dl.bintray.com/scf37/sbt-plugins"))(Resolver.ivyStylePatterns)
addSbtPlugin("me.scf37.buildprops" % "sbt-build-properties" % "1.0.7")
