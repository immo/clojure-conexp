;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; This file has been written by Immanuel Albrecht

(ns conexp.contrib.j3d
  (:use conexp.contrib.gui.util
    conexp.contrib.gui.editors.context-editor.widgets)
  (:import
    [conexp.contrib.gui.editors.context-editor.widgets widget]
    [com.sun.j3d.utils.universe SimpleUniverse]
    [com.sun.j3d.utils.geometry ColorCube Sphere]
    [javax.media.j3d BranchGroup Canvas3D]
    [java.awt GraphicsConfiguration BorderLayout]))

(defn hello-world
  []
  (let [ universe (SimpleUniverse.)
         branch (BranchGroup.)
         cube (ColorCube. 0.3) ]
    (.addChild branch cube)
    (.setNominalViewingTransform (.getViewingPlatform universe))
    (.addBranchGraph universe branch)
    universe))

(defwidget j3d-canvas-control [widget] [widget universe contents])

(defn-swing make-j3d-canvas-control
  "Returns a j3d canvas control"
  []
  (let [ config (SimpleUniverse/getPreferredConfiguration)
         canvas (Canvas3D. config)
         universe (SimpleUniverse. canvas)
         contents (BranchGroup.)
         widget (j3d-canvas-control. canvas universe contents)]
    (.setNominalViewingTransform (.getViewingPlatform universe))
    (.setCapability contents BranchGroup/ALLOW_DETACH)
    (.addBranchGraph universe contents)
    widget))


(defn-swing add-content
  "Adds some content object to the given j3d-canvas-control."
  [canvas content]
  (assert (keyword-isa? canvas j3d-canvas-control))
  (let [ contents (:contents canvas)
         universe (:universe canvas)]
    (.detach contents)
    (.addChild contents content)
    (.addBranchGraph universe contents)))
