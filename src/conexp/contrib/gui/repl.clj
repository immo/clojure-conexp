;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.contrib.gui.repl
  (:import [javax.swing.text PlainDocument]
	   [java.io PushbackReader StringReader PipedWriter PipedReader
	            PrintWriter CharArrayWriter]
	   [javax.swing KeyStroke AbstractAction JTextArea JScrollPane JFrame]
	   [java.awt Font Color])
  (:use [conexp.base :only (defvar-)]
	[conexp.contrib.gui.util :as util])
  (:require [conexp.contrib.gui.repl-utils :as repl-utils])
  (:use	[clojure.contrib.pprint :only (write)]
	clojure.contrib.swing-utils)
  (:require clojure.main))

;;; REPL Process

(defvar- *print-stack-trace-on-error* false
  "Controls whether the REPL prints a full stack strace or not.")

(defn- eof-ex?
  "Returns true iff given throwable is an \"EOF while reading\" or \"Write
  end dead\" exception not thrown from the repl." ; hopefully
  [throwable]
  (and (not (instance? clojure.lang.Compiler$CompilerException throwable))
       (.getMessage throwable)
       (or (re-matches #".*EOF while reading.*" (.getMessage throwable))
	   (re-matches #".*Write end dead.*" (.getMessage throwable)))))

(defn- create-clojure-repl-process
  "This function creates an instance of clojure repl using piped in and out.
   It returns a map of two functions repl-fn and result-fn - first function
   can be called with a valid clojure expression and the results are read using
   the result-fn. In the new repl frame is bound to *main-frame*.

   Based on org.enclojure.repl.main/create-clojure-repl

   Copyright (c) ThorTech, L.L.C.. All rights reserved.
   The use and distribution terms for this software are covered by the
   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
   which can be found in the file epl-v10.html at the root of this distribution.
   By using this software in any fashion, you are agreeing to be bound by
   the terms of this license.
   You must not remove this notice, or any other, from this software.

   Author: Eric Thorsen, Narayan Singhal"
  [frame]
  (let [cmd-wtr    (PipedWriter.)
        result-rdr (PipedReader.)
        piped-in   (clojure.lang.LineNumberingPushbackReader. (PipedReader. cmd-wtr))
        piped-out  (PrintWriter. (PipedWriter. result-rdr))
        repl-thread-fn #(clojure.main/with-bindings
			  (binding [*print-stack-trace-on-error* *print-stack-trace-on-error*,
				    *in* piped-in,
				    *out* piped-out,
				    *err* (PrintWriter. *out*),
				    repl-utils/*main-frame* frame]
			    (try
			     (clojure.main/repl
                              :init (fn []
				      (in-ns 'user)
				      (use 'conexp.main)
				      (require '[conexp.contrib.gui.repl-utils :as gui]))
                              :caught (fn [e]
					(if *print-stack-trace-on-error*
                                          (.printStackTrace e *out*)
                                          (prn (clojure.main/repl-exception e)))
					(when (eof-ex? e)
					  (throw e))
					(flush))
                              :need-prompt (constantly true))
                            (catch Exception ex
                              (prn "REPL closing")))))
	repl-thread (Thread. repl-thread-fn)]
    (.start repl-thread)
    {:repl-thread repl-thread
     :repl-fn (fn [cmd]
		(.write cmd-wtr cmd)
		(.flush cmd-wtr))
     :result-fn (fn []
		  (loop [#^CharArrayWriter wtr (CharArrayWriter.)]
		    (.write wtr (.read result-rdr))
		    (if (.ready result-rdr)
		      (recur wtr)
		      (.toString wtr))))}))

(defn repl-in
  "Sends string to given repl with a newline appended."
  [rpl string]
  ((:repl-fn rpl) (str string "\n")))

(defn repl-out
  "Reads result from given repl."
  [rpl]
  (let [result ((:result-fn rpl))]
    result))

(defn repl-interrupt
  "Interrupts (stops) given repl process."
  [rpl]
  (.stop (:repl-thread rpl)))

(defn repl-alive?
  "Tests whether given repl process is still alive or not."
  [rpl]
  (.isAlive (:repl-thread rpl)))


;;; Display

(defn- balanced?
  "Checks whether given string has balanced (,)-pairs."
  ([string]
     (balanced? string 0))
  ([string paran-count]
     (cond
       (> 0 paran-count)
       false
       (empty? string)
       (= 0 paran-count)
       :else
       (recur (rest string)
	      (cond
		(= \( (first string)) (inc paran-count)
		(= \) (first string)) (dec paran-count)
		:else paran-count)))))

(defprotocol ReplProcess
  (getReplThreadMap [frame] "Returns the repl thread map of the given frame."))

(defn- make-clojure-repl
  "Returns for the given frame a PlainDocument containing a clojure
  repl together with the correspongind repl- and output-thread. The
  output-thread has to be started manually afterwards."
  [frame]
  (let [last-pos (ref 0),
	repl-thread (create-clojure-repl-process frame),
	#^PlainDocument
	repl-container (proxy [PlainDocument conexp.contrib.gui.repl.ReplProcess] []
                         (getReplThreadMap []
                           repl-thread)
			 (remove [off len]
			   (when (>= (- off len -1) @last-pos)
			     (proxy-super remove off len)))
			 (insertString [off string attr-set]
			   (when (>= off @last-pos)
			     (proxy-super insertString off string attr-set)
			     (when (and (= string "\n")
					(= off (- (-> this .getEndPosition .getOffset)
						  2)))
			       (let [input (.getText this
						     (- @last-pos 1)
						     (- (-> this .getEndPosition .getOffset) @last-pos 1))]
				 (when (balanced? input)
				   (repl-in repl-thread input)))))))
	insert-result (fn [result]
			(.insertString repl-container
				       (.getLength repl-container)
				       result
				       nil)
			(dosync
			 (ref-set last-pos (.getLength repl-container))))
	output-thread (Thread. (fn []
				 (while (repl-alive? repl-thread)
				   (let [result (repl-out repl-thread)]
				     (do-swing
				      (insert-result result))))))]
    [repl-container repl-thread output-thread]))

;;;

(defn- add-input-event
  "Adds a given input-event for the given key-sequence to
  component. key-sequence must be a string describing a valid key
  sequence and input-event must be a string."
  [component key-sequence input-event]
  (.. component getInputMap (put (KeyStroke/getKeyStroke key-sequence) input-event)))

(defn- add-action-event
  "Adds to component for a given input-event the callback to be called
  when input-event is triggered. input must be a string describing an
  input event and callback must be a function of no arguments."
  [component input-event callback]
  (.. component getActionMap (put input-event (proxy [AbstractAction] []
                                                (actionPerformed [_]
                                                  (callback))))))

(defn- into-text-area
  "Puts repl-container (a PlainDocument) into a JTextArea adding some hotkeys."
  [repl-container repl-thread]
  (let [#^JTextArea repl-window (JTextArea. repl-container)]
    (doto repl-window
      (add-input-event "control C" "interrupt")
      (add-action-event "interrupt" #(repl-interrupt repl-thread)))))

;;;

(defn make-repl
  "Creates a default Clojure REPL for frame, returning a pair of an
  embedded REPL (in a JScrollPane) and the corresponding output
  thread."
  [frame]
  (let [[repl-container repl-thread output-thread] (make-clojure-repl frame),
	rpl (into-text-area repl-container repl-thread)]
    (doto rpl
      (.setFont (Font. "Monospaced" Font/PLAIN 16))
      (.setBackground Color/BLACK)
      (.setForeground Color/WHITE)
      (.setCaretColor Color/RED))
    (.start output-thread)
    (JScrollPane. rpl)))

(defn get-repl-thread
  "Returns for a given frame its corresponding repl-thread, if existent."
  [frame]
  (let [repl-container (get-component frame
                                      (fn [thing]
                                        (and (= (class thing) JTextArea)
                                             (util/implements-interface? (class (.getDocument thing))
                                                                         conexp.contrib.gui.repl.ReplProcess))))]
    (when repl-container
      (.. repl-container getDocument getReplThreadMap))))

;;;

nil
