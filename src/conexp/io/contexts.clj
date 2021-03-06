;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.io.contexts
  (:use conexp.base
	conexp.fca.contexts
	conexp.io.util)
  (:use [clojure.contrib.io :only (with-out-writer, read-lines, reader)]
	[clojure.contrib.lazy-xml :exclude (attributes)]
	clojure.contrib.prxml
	[clojure.contrib.string :only (split)])
  (:import [java.io PushbackReader]))


;;; Input format dispatch

(define-format-dispatch "context")
(set-default-context-format! :simple)

;;; Formats

;; Simple conexp-clj Format

(add-context-input-format :simple
			  (fn [rdr]
			    (= "conexp-clj simple" (.readLine rdr))))

(defmethod write-context :simple [_ ctx file]
  (with-out-writer file
    (println "conexp-clj simple")
    (prn {:context [(objects ctx)
		    (attributes ctx)
		    (incidence ctx)]})))

(defmethod read-context :simple [file]
  (with-in-reader file
    (let [_        (get-line)
	  hash-map (binding [*in* (PushbackReader. *in*)]
		     (read)),
	  context  (:context hash-map)]
      (when-not context
	(illegal-argument "File " file " does not contain a context."))
      (apply make-context context))))


;; Burmeister Format

(add-context-input-format :burmeister
			  (fn [rdr]
			    (= "B" (.readLine rdr))))

(defmethod write-context :burmeister [_ ctx file]
  (with-out-writer file
    (println \B)
    (println)
    (println (count (objects ctx)))
    (println (count (attributes ctx)))
    (println)
    (doseq [g (objects ctx)] (println g))
    (doseq [m (attributes ctx)] (println m))
    (let [inz (incidence ctx)]
      (doseq [g (objects ctx)]
	(doseq [m (attributes ctx)]
	  (print (if (inz [g m]) "X" ".")))
	(println)))))

(defmethod read-context :burmeister [file]
  (with-in-reader file
    (let [_                    (get-lines 2)    ; "B\n\n"

	  number-of-objects    (Integer/parseInt (get-line))
	  number-of-attributes (Integer/parseInt (get-line))

	  _                    (get-line)	  ; "\n"

	  seq-of-objects       (get-lines number-of-objects)
	  seq-of-attributes    (get-lines number-of-attributes)]
      (loop [objs seq-of-objects
	     incidence #{}]
	(if (empty? objs)
	  (make-context-nc (set seq-of-objects)
			   (set seq-of-attributes)
			   incidence)
	  (let [line (get-line)]
	    (recur (rest objs)
		   (union incidence
			  (set-of [(first objs) (nth seq-of-attributes idx-m)]
				  [idx-m (range number-of-attributes)
				   :when (#{\X,\x} (nth line idx-m))])))))))))

;; XML helpers

(defn- find-tags [seq-of-hashes tag]
  (for [hash seq-of-hashes :when (= tag (:tag hash))] hash))

(defn- find-tag [seq-of-hashes tag]
  (first (find-tags seq-of-hashes tag)))

(defn- trim [str]
  (.trim str))

(defn- hash-from-pairs [pairs]
  (into {} pairs))

;; ConExp

(add-context-input-format :conexp
			  (fn [rdr]
			    (try
			     (= :ConceptualSystem (-> (parse-seq rdr) first :name))
			     (catch Exception _))))

(defmethod read-context :conexp [file]
  (with-in-reader file
    (let [xml-tree (parse-trim *in*)
	  contexts (:content (first (find-tags (:content xml-tree) :Contexts)))]
      (cond
	(= 0 (count contexts))
	(throw (IllegalArgumentException. (str "No context specified in " file)))
	(< 1 (count contexts))
	(throw (IllegalArgumentException. (str "More than one context specified in " file))))
      (let [context (first contexts)
	    atts-map (find-tag (:content context) :Attributes)
	    objs-map (find-tag (:content context) :Objects)

	    obj-idxs-map (hash-from-pairs
			  (for [obj-map (:content objs-map)]
			    [(-> obj-map :content (find-tag :Name) :content first trim)
			     (set-of (get-in att [:attrs :AttributeIdentifier])
				     [att (-> obj-map :content (find-tag :Intent) :content)])]))

	    idx-atts-map (hash-from-pairs
			  (for [att-map (:content atts-map)]
			    [(get-in att-map [:attrs :Identifier])
			     (-> att-map :content (find-tag :Name) :content first trim)]))]
	(make-context-nc (set (keys obj-idxs-map))
			 (set (vals idx-atts-map))
			 (set-of [g (idx-atts-map idx) ]
				 [[g att-idxs] obj-idxs-map
				  idx att-idxs]))))))

(defn- ctx->xml-vector [ctx id]
  (let [ctx-atts (zipmap (attributes ctx) (iterate inc 0))
	ctx-objs (objects ctx)
	attributes (vector :Attributes
			   (map (fn [[att id]]
				  [:Attribute {:Identifier id}
				   [:raw! (str "\n          <Name>" att "</Name>")]])
				ctx-atts))
	objects (vector :Objects
			(for [obj ctx-objs]
			  [:Object
			   [:raw! (str "\n          <Name>" obj "</Name>")]
			   (vector :Intent
				   (for [att (object-derivation ctx #{obj})]
				     [:HasAttribute {:AttributeIdentifier (ctx-atts att)}]))]))]
    [:Context {:Identifier "0", :Type "Binary"}
     attributes
     objects]))

(defmethod write-context :conexp [_ ctx file]
  (binding [*prxml-indent* 2]
    (with-out-writer file
      (prxml [:decl! {:version "1.0"}])
      (prxml [:ConceptualSystem
	      [:Version {:MajorNumber "1", :MinorNumber "0"}]
	      [:Contexts (ctx->xml-vector ctx 0)]]))))


;; Galicia (.bin.xml)

(add-context-input-format :galicia
			  (fn [rdr]
			    (try
			     (let [xml-tree (parse-seq rdr)]
			       (and (= :Galicia_Document (-> xml-tree first :name))
				    (= :BinaryContext (-> xml-tree second :name))))
			     (catch Exception _))))

(defmethod write-context :galicia [_ ctx file]
  (let [atts (apply hash-map (interleave (attributes ctx) (iterate inc 0)))
	objs (apply hash-map (interleave (objects ctx) (iterate inc 0)))

	atts-vector (sort #(< (atts %1) (atts %2)) (attributes ctx))
	objs-vector (sort #(< (objs %1) (objs %2)) (objects ctx))]
    (binding [clojure.contrib.prxml/*prxml-indent* 2]
      (with-out-writer file
	(prxml [:decl! {:vecsion "1.0"}])
	(prxml [:Galicia_Document
		[:BinaryContext {:numberObj (str (count objs-vector)),
				 :numberAtt (str (count atts-vector))}
		 [:Name "conexp-clj generated context"]
		 (for [obj objs-vector]
		   [:raw! (str "\n    <Object>" obj "</Object>")])
		 (for [att atts-vector]
		   [:raw! (str "\n    <Attribute>" att "</Attribute>")])
		 (for [[g m] (incidence ctx)]
		   [:BinRel {:idxO (str (objs g)),
			     :idxA (str (atts m))}])]])))))

(defmethod read-context :galicia [file]
  (with-in-reader file
    (let [ctx-xml-tree (-> (parse-trim *in*) :content first)

	  nr-objs (Integer/parseInt (-> ctx-xml-tree :attrs :numberObj))
	  nr-atts (Integer/parseInt (-> ctx-xml-tree :attrs :numberAtt))

          ;; can be done better (one run instead of three)
	  objs (map (comp first :content) (filter #(= (:tag %) :Object) (-> ctx-xml-tree :content)))
	  atts (map (comp first :content) (filter #(= (:tag %) :Attribute) (-> ctx-xml-tree :content)))
	  idxs (map #(vector (Integer/parseInt (:idxO (:attrs %)))
			     (Integer/parseInt (:idxA (:attrs %))))
		    (filter #(= (:tag %) :BinRel) (-> ctx-xml-tree :content)))]
      (make-context-nc objs
		       atts
		       (set-of [(nth objs idxO) (nth atts idxA)]
			       [[idxO idxA] idxs])))))


;; Colibri (.bri, .con)

;; Note: Colibri cannot store empty columns. They get lost when writing

(add-context-input-format :colibri
			  (fn [rdr]
			    (let [comment #"^\s*#.*$"
				  blank   #"^\s*$"
				  row     #"^\s*.+\s*:.*;\s*$"]
			    (forall [line (read-lines rdr)]
			       (or (re-matches comment line)
				   (re-matches blank line)
				   (re-matches row line))))))

(defmethod write-context :colibri [_ ctx file]
  (if (some (fn [m] (and (string? m) (some #(#{\ ,\:,\;} %) m))) (attributes ctx))
    (illegal-argument
     "Cannot export to :colibri format, object or attribute names contain invalid characters."))
  (if (not (empty? (difference (attributes ctx) (set-of m [[g m] (incidence ctx)]))))
    (illegal-argument
     "Cannot export to :colibri format, context contains empty columns."))
  (with-out-writer file
    (doseq [g (objects ctx)]
      (print g)
      (print ":")
      (doseq [m (object-derivation ctx #{g})]
	(print "\t")
	(print m))
      (print ";\n"))))

(defmethod read-context :colibri [file]
  (loop [in (reader file)
	 objs #{}
	 inz #{}]
    (let [line (.readLine in)]
      (cond
	(not line)
	(make-context-nc objs (set-of m [[g m] inz]) inz)
	(or (re-matches #"^\s*$" line)     ; blank
	    (re-matches #"^\s*#.*$" line)) ; comment
	(recur in objs inz)
	:else
	(let [[_ g atts] (re-matches #"^\s*(.+)\s*:\s*(.+)?\s*;\s*(?:#.*)?$" line)
	      atts (and atts (split #"\s+" atts))]
	  (recur in (conj objs g) (union inz (set-of [g m] [m atts]))))))))


;; Comma Seperated Values (.csv)

(add-context-input-format :csv
			  (fn [rdr]
			    (try
			     (re-matches #"^[^,]+,[^,]+$" (.readLine rdr))
			     (catch Exception _))))

(defmethod read-context :csv [file]
  (let [in (reader file)]
    (loop [inz #{}]
      (let [line (.readLine in)]
	(if (not line)
	  (make-context-nc (set-of g [[g m] inz])
			   (set-of m [[g m] inz])
			   inz)
	  (let [[_ g m] (re-matches #"^([^,])+,([^,])+$" line)]
	    (recur (conj inz [g m]))))))))

(defmethod write-context :csv [_ ctx file]
  (if (some (fn [x] (and (string? x) (some #(= \, %) x))) (concat (objects ctx) (attributes ctx)))
    (illegal-argument "Cannot export to :csv format, object or attribute names contain \",\"."))
  (with-out-writer file
    (doseq [[g m] (incidence ctx)]
      (println (str g "," m)))))

(defn list-context-formats
  "Returns a list of known context formats, with the default value as
  first element."
  []
  (let [ def (get-default-context-format)
         other (sort (filter (fn [x] (not= x def))
                       (get-known-context-input-formats))) ]
    (conj other def)))
;;; TODO

;; slf
;; csc
;; csx?
;; tuples
;; out only: TeX

;;;

nil
