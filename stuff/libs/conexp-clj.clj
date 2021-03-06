;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(use 'conexp.main)

(when (some #{"--gui"} *command-line-args*)
  (require 'conexp.contrib.gui)
  (@(ns-resolve 'conexp.contrib.gui 'gui) :default-close-operation javax.swing.JFrame/EXIT_ON_CLOSE))

nil
