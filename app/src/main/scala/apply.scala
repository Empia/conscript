package conscript

import scala.util.control.Exception.{catching,allCatch}
import java.io.File

object Apply extends Launch with Display {
  def config(user: String, repo: String, name: String, launch: String) = {
    val launchconfig = configdir(user / repo / name / "launchconfig")

    val place = windows map { _ =>
      homedir(("bin" / "%s.bat") format name)
    } getOrElse { homedir("bin" / name) }

    write(launchconfig, launch + boot).orElse {
      write(place, script(launchconfig)) orElse {
        allCatch.opt {
          // java 1.6+ has this useful method
          place.asInstanceOf[
            { def setExecutable(b: Boolean): Boolean}
          ].setExecutable(true)
        }.filter { _ == true } match {
          case None => Some("Unable set as executable: " + place)
          case _ => None
        }
      }
    }.toLeft {
      allCatch.opt {
        windows map { _ =>
          Runtime.getRuntime.exec("""cmd /c "%s" --version""" format (place))
        } getOrElse { Runtime.getRuntime.exec("%s --version" format (place)) }          
      }.filter { _ == 0 } match {
        case _ => None // ignore errors since the app might not have `--version`
      }
      
      "Conscripted %s/%s to %s".format(user, repo, place)  
    }
  }

  def bootdir = configdir("boot")
  def write(file: File, body: String) =
    mkdir(file) orElse {
      catching(classOf[java.io.IOException]).either {
        val fw = new java.io.FileWriter(file)
        fw.write(body)
        fw.close()
      }.left.toOption.map { e => "Error writing " + file }
   }

  val javaopt = "-Xmx1G"
  def script(launchconfig: File) = windows map { _ =>
    """@echo off""" + "\r\n" +
    ("""java %s -jar "%s" "@file://%s" %%*""" + "\r\n") format (javaopt, configdir(sbtlaunchalias),
      forceslash(launchconfig.getCanonicalPath))
  } getOrElse {
    """#!/bin/sh
      |java %s -jar %s @%s "$@"
      |""" .stripMargin format (javaopt, configdir(sbtlaunchalias), launchconfig.getCanonicalPath)
  }
  val boot = """
            |[boot]
            |  directory: %s
            |""".stripMargin.format(forceslash(bootdir.toString))
}
