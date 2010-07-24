;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; This file has been written by Immanuel Albrecht

(ns conexp.contrib.j3d
  (:import 
    [com.sun.j3d.utils.universe SimpleUniverse]
    [com.sun.j3d.utils.geometry ColorCube Sphere]
    [javax.media.j3d BranchGroup]))

(defn hello-world
  []
  (let [ universe (SimpleUniverse.)
         branch (BranchGroup.)
         cube (ColorCube. 0.3) ]
    (.addChild branch cube)
    (.setNominalViewingTransform (.getViewingPlatform universe))
    (.addBranchGraph universe branch)
    universe))
