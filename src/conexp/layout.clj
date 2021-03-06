;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.layout
  (:use conexp.base
	conexp.layout.layered
	conexp.layout.common))

;;;

(defvar *standard-layout-function* simple-layered-layout
  "Standard layout function. Call on a lattice to get a layout.")

(defn inf-additive-layout
  "Returns an infimum additive layout for lattice."
  [lattice]
  (to-inf-additive-layout lattice (*standard-layout-function* lattice)))

;;;

nil
