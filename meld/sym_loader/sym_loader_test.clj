(defn sym-load-lineseq [lseq]
  (loop [l lseq
         h (hash-map)]
    (if-let [s (first l)]
      (recur (rest l) (assoc h s (.length s)))
      h)))

(defn sym-lookup [m ^String file]
  (with-open [r (clojure.java.io/reader file)]
    (loop [l (line-seq r)
           found 0
           not-found 0]
      (if-let [s (first l)]
        (if (get m s)
          (recur (rest l) (inc found) not-found)
          (recur (rest l) found (inc not-found)))
        (println (format "Found: %d, Not Found: %d" found not-found))))))

(defn sym-load-file [^String file]
  (with-open [r (clojure.java.io/reader file)]
    (sym-load-lineseq (line-seq r))))

(defn sym-load-test [^String file]
  (sym-lookup (sym-load-file file) file))
