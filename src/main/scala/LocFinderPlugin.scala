package eu {
package getintheloop {
  
  import java.io.{File,FileFilter,FilenameFilter}
  import scala.io.Source
  import scala.xml.XML
  import sbt._

  trait LocFinderPlugin extends DefaultWebProject with LocaliztionKeyGrabber {
    // description
    val LocFinderDescription = "List all the lift:loc elements in your web application"
    // command
    lazy val findLocs = findLocsAction
    // action
    protected def findLocsAction = 
      task(findLocsTask) dependsOn(compile) describedAs(LocFinderDescription)
    
    private def findLocsTask = {
      log.info("Here's a list of all the localization keys in your webapp: \n")
      println("")
      scanDir(webappPath.asFile).foldLeft(Set[String]())((set,key) => 
        set + key).toList.sort(_ < _).foreach(println)
      println("")
      None
    }
    
  }

  sealed trait LocaliztionKeyGrabber {
    // Helper function to pull out keys from an XML (xhtml, html, etc) file
    def parseXML (input : File) : Seq[String] =
      try {
        (XML.loadFile(input) \\ "loc").filter(node => node.prefix == "lift").map(node => node.attribute("locid") match {
          case Some(locid) => locid.toString.replace(" ","\\ ") + "=" // Just return the key
          case None => node.child.mkString("","","=").replace(" ","\\ ")
        })
      } catch {
        case e : Exception => { format("Error parsing %s : %s", input, e.getMessage); exit(1) }
      }
    
    // Helper function to pull keys out of a Lift scala file. Pretty naieve, but until I figure out 
    // how to hook into the compiler/AST this isn't going to be pretty or clever
    val scalaRegex = """\?\s*\(\s*\"([^\"]*)\"\s*\)""".r
    def parseScala (input : File) : Seq[String] = 
      Source.fromFile(input).getLines.flatMap({line => scalaRegex.findAllIn(line).matchData.map(grp => grp.group(0).replace(" ","\\ ") + "=")}).toList
    
    // Define filters for scanning directories
    val scalaFileFilter = new FilenameFilter() {
      override def accept(parent : File, name : String) = name.toLowerCase.endsWith(".scala")
    }
    
    val xmlFileFilter = new FilenameFilter() {
      override def accept(parent : File, name : String) = {
        val ln = name.toLowerCase
        (ln != "web.xml") && (ln.endsWith(".xml") || ln.endsWith(".html") || ln.endsWith(".xhtml"))
      }
    }
    
    val directoryFilter = new FileFilter () {
      override def accept(file : File) = file.isDirectory
    }
    
    def scanDir(dir : File) : Seq[String] = {
      val scalaKeys = dir.listFiles(scalaFileFilter).flatMap(parseScala)
      val xmlKeys = dir.listFiles(xmlFileFilter).flatMap(parseXML)
      val dirKeys = dir.listFiles(directoryFilter).flatMap(scanDir)
      scalaKeys ++ xmlKeys ++ dirKeys
    }
    
  }
  
}}
