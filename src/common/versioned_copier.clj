(defn md5file [file]
  (let [input (java.io.FileInputStream. file)
        digest (java.security.MessageDigest/getInstance "MD5")
        stream (java.security.DigestInputStream. input digest)
        bufsize (* 128 1024)
        buf (byte-array bufsize)]

  (while (not= -1 (.read stream buf 0 bufsize)))
  (apply str (map (partial format "%02x") (.digest digest)))))

(defn walk-dir [dir]
  (remove #(.isDirectory %)
    (file-seq (java.io.File. dir))))

;; list of index-db's
(defn index-db-list [idxdir]
  (filter  #(re-matches #"file_index.*" %)
    (map #(.getName %)
     (file-seq
       (java.io.File.
         (str idxdir "/index"))))))

;; greatest index-db with time-stamp >= "ts"
(defn prev-index-db [idxdir ts]
  (let [prev-ts
    (first
      (sort #(> %1 %2)
        (filter #(<= % ts)
          (map
            #(Integer/parseInt %)
            (map
              #(clojure.string/replace % #"file_index." "")
              (index-db-list idxdir))))))]
    (if prev-ts
      (str idxdir "/index/" prev-ts)
      nil)))

(defn copy-one-file [src dst]
  (clojure.java.io/copy (clojure.java.io/file src) (clojure.java.io/file dst)))

(defn mkdir [dir]
  (let [d (java.io.File. dir)]
    (cond
      (.isDirectory d) nil
      :else)))

(defn copy-all-files [locdir bkdir ts]
  (let [basedir (str 
