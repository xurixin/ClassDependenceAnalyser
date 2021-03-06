/*
 * ClassDependenceAnalyser - A tool for java classes dependence analysis
 * Copyright (C) 2016 <chentaov5@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */
package com.github.jllk.analyser

import java.io.File
import java.net.{URLClassLoader, URL}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author chentaov5@gmail.com
  *
  */
object Analyser {
  def notCareClass(fullClassName: String): Boolean =
    fullClassName.startsWith("java") ||
    fullClassName.startsWith("scala") ||
    fullClassName.startsWith("\"[") ||
    (fullClassName.startsWith("android") && !fullClassName.startsWith("android/support"))
}

class Analyser(private val dependenceJarPath: List[File]) {

  import Analyser._

  def analysis(fullClassName: String): mutable.Set[String] = {
    val dependentClasses = mutable.Set[String]()
    val importDependence = analysisImportDependence(fullClassName)
    importDependence
      .foreach(c => {
        dependentClasses += c
        dependentClasses ++= analysisInheritDependence(c)
      })
    dependentClasses
  }

  private def analysisImportDependence(fullClassName: String): List[String] = {
    val dependentClasses = new ListBuffer[String]()
    val classpath = dependenceJarPath.map(f => s"-classpath ${f.toPath}") mkString " "
    val classReport = ProcessUtils.exec(s"javap -verbose $classpath ${fullClassName.replace('.', '/')}")
    val lines = classReport.split('\n')
    lines
      .filter(l => l.contains("= Class") && !l.contains("\"[Ljava/lang/Object;\""))
      .foreach(l => dependentClasses += l.substring(l.indexOf("//") + 2).replaceAll(" ", "").replaceAll("/", "\\.").trim())
    dependentClasses
      .filter(notCareClass)
      .toList
  }

  private def analysisInheritDependence(fullClassName: String): List[String] = {
    val urls = ListBuffer[URL]()
    dependenceJarPath.foreach(f => urls += f.toURI.toURL)
    val classLoader = new URLClassLoader(urls.toArray)
    doClassInheritSearch(fullClassName, classLoader)
  }

  private def doClassInheritSearch(fullClassName: String, classLoader: URLClassLoader): List[String] = {
    if (notCareClass(fullClassName)) {
      List.empty[String]
    } else {

      val dependentClasses = mutable.Set[String]()
      dependentClasses += fullClassName
      dependentClasses ++= analysisImportDependence(fullClassName)
      dependentClasses.foreach(fullClassName => {
        val targetClass: Either[Class[_], Exception] =
          try
            Left(classLoader.loadClass(fullClassName))
          catch {
            case e: ClassNotFoundException => Right(e)
            case e: Exception => Right(e)
          }

        targetClass match {
          case Left(c) =>
            val superclass = c.getSuperclass
            if (superclass != null) {
              dependentClasses ++= doClassInheritSearch(superclass.getName, classLoader)
            }
            c.getInterfaces.foreach(i => dependentClasses ++= doClassInheritSearch(i.getName, classLoader))
          case Right(e) =>
            println(s"[doClassInheritSearch] exception happened: ${e.getMessage}, please check your dependenceJarPath.")
        }

      })
      dependentClasses.toList
    }
  }
}
