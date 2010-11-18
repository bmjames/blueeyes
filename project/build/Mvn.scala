import sbt._
import util.matching.Regex
import org.apache.ivy.util.url.CredentialsStore

object Mvn {
  
  def apply(projectRoot: Path, input: Option[String], log: Logger, command: String, mvmArgs: String*): String = {

    val credentials = CredentialsStore.INSTANCE.getCredentials("Sonatype Nexus Repository Manager", "oss.sonatype.org")

    val args = List("mvn", "-s", (projectRoot / "project" / "build" / "sonatype.mvn.xml").asFile.getAbsolutePath, command, "-Dnexus.url=https://oss.sonatype.org", "-Dnexus.username=" + credentials.getUserName(), "-Dnexus.password=" + credentials.getPasswd()) ++ mvmArgs

    ExtermalProcess(args, input, log)
  }
}

object NexusStagingList {
  def apply(projectRoot: Path, log: Logger) = {

    log.info("Getting Nexus staging repositories list.")

    val output = Mvn(projectRoot, None, log, "nexus:staging-list")

    val regexp = new Regex("(comgithubblueeyes-\\d+)")

    regexp.findAllIn(output).toList.removeDuplicates
  }
}

object NexusStagingClose {
  def apply(projectRoot: Path, repositoryId: String, log: Logger) = {

    log.info("Closing staging repository: %s.".format(repositoryId))

    Mvn(projectRoot, None, log, "nexus:staging-close", "-Dnexus.repositoryId=" + repositoryId, "-Dnexus.description=Closing repository " + repositoryId)
  }
}

object NexusStagingRelease {
  def apply(projectRoot: Path, repositoryId: String, log: Logger) = {

    log.info("Releasing staging repository: %s.".format(repositoryId))

    Mvn(projectRoot, Some("releases"), log, "nexus:staging-release", "-Dnexus.repositoryId=" + repositoryId, "-Dnexus.description=repository release: " + repositoryId)
  }
}