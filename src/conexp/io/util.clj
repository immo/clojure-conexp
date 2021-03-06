;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.io.util
  (:use conexp.base)
  (:use [clojure.contrib.io :only (reader)]))

;;;

(defn get-line
  "Reads one line from *in*."
  []
  (read-line))

(defn get-lines
  "Reads n line from *in*."
  [n]
  (doall (take n (repeatedly #(get-line)))))

(defmacro with-in-reader
  "Opens file with reader and binds it to *in*."
  [file & body]
  `(with-open [input# (reader ~file)]
     (binding [*in* input#,
               *read-eval* false]
       ~@body)))

;;; Format dispatch framework macro

(defmacro define-format-dispatch
  "Defines for name the functions write-name, read-name,
  add-name-input-format, get-known-name-input-formats and
  find-name-input-format. You can then add new formats with
  add-name-input-format and read-name will automatically dispatch in
  the format determined from its only argument."
  [name]
  (let [add   (symbol (str "add-" name "-input-format")),
	get   (symbol (str "get-known-" name "-input-formats")),
	find  (symbol (str "find-" name "-input-format")),
	write (symbol (str "write-" name)),
	read  (symbol (str "read-" name)),
	get-default-write (symbol (str "get-default-" name "-format")),
	set-default-write (symbol (str "set-default-" name "-format!"))]
  `(do
     (let [known-context-input-formats# (ref {})]
       (defn- ~add [name# predicate#]
	 (dosync
	  (alter known-context-input-formats# assoc name# predicate#)))

       (defn- ~get []
	 (keys @known-context-input-formats#))

       (defn- ~find [file#]
	 (first
	  (for [[name# predicate#] @known-context-input-formats#
		:when (with-open [in-rdr# (reader file#)]
			(predicate# in-rdr#))]
	    name#)))

       nil)

     (let [default-write-format# (atom nil)]
       (defn ~get-default-write
	 ~(str "Returns default write format for " name "s.")
	 []
	 (when (nil? @default-write-format#)
	   (illegal-state "No default write format specified for " ~name "."))
	 @default-write-format#)

       (defn ~set-default-write
	 ~(str "Sets default write format for " name "s to format.")
	 [format#]
	 (reset! default-write-format# format#))

       nil)

     (defmulti ~write
       ~(str "Writes " name " to file using format.")
       {:arglists (list [(symbol "format") (symbol ~name) (symbol "file")]
			[(symbol ~name) (symbol "file")])}
       (fn [& args#]
	 (cond
	  (= 2 (count args#)) ::default-write
	  (= 3 (count args#)) (first args#)
	  :else (illegal-argument "Invalid number of arguments in call to " ~write "."))))
     (defmethod ~write :default [format# _# _#]
       (illegal-argument "Format " format# " for " ~name " output is not known."))
     (defmethod ~write ::default-write [ctx# file#]
       (~write (~get-default-write) ctx# file#))

     (defmulti ~read
       ~(str "Reads "name " from file, automatically determining the format used.")
       {:arglists (list [(symbol "file")])}
       ~find)
     (defmethod ~read :default [file#]
       (illegal-argument "Cannot determine format of " ~name " in " file#))

     nil)))

;;;

nil
