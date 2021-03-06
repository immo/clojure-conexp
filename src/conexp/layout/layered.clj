;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.layout.layered
  (:use conexp.base
	conexp.layout.util
	[conexp.layout.base :only (make-layout)]
	[conexp.fca.lattices :only (base-set order)])
  (:use [clojure.contrib.graph :only (directed-graph, dependency-list, remove-loops)]))

(ns-doc "Basic namespace for lattice layouts.")


;;; Simple Layered Layout

(defn- lattice->graph
  "Converts given lattice to it's corresponding graph with loops
  removed."
  [lattice]
  (remove-loops
   (struct-map directed-graph
     :nodes (base-set lattice)
     :neighbors (memoize
		 (fn [x]
		   (let [order (order lattice)]
		     (filter #(order [% x]) (base-set lattice))))))))

(defn layers
  "Returns the layers of the given lattice, that is sequence of points
  with equal heights."
  [lattice]
  (dependency-list (lattice->graph lattice)))

(defn- layer-coordinates
  "Assigns coordinates to a given layer such that it is centerer
  around 0 at height given by number."
  [number layer]
  (let [start (double (- (/ (- (count layer) 1) 2)))]
    (interleave layer
		(map #(vector % number)
		     (iterate inc start)))))

(defn simple-layered-layout
  "Simple layered layout for lattice visualization."
  [lattice]
  (let [positions (apply hash-map
			 (apply concat
				(map layer-coordinates
				     (iterate inc 0)
				     (layers lattice))))]
    (scale-layout [0.0 0.0] [200.0 200.0]
		  (make-layout positions (edges lattice)))))


;;;

nil
