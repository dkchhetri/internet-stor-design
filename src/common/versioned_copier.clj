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

(defn mkdir [dir]
  (let [d (java.io.File. dir)]
    (cond
      (.isDirectory d) true
      :else (.mkdirs d))))

;; get normalized path
(defn normalized-path [path]
  (.getCanonicalPath (java.io.File. path)))

;; copy one file, making directory if needed
(defn copy-one-file [src dst]
  (let [parent (.getParent (java.io.File. dst))]
    (mkdir parent)
    (clojure.java.io/copy (clojure.java.io/file src) (clojure.java.io/file dst))))

(defn copy-all-files [locdir bkdir ts]
  (let [data_dir (normalized-path (str bkdir "/data/" ts))
        idx_db (normalized-path (str bkdir "/.metadata/index/file_index." ts))]
       (.createNewFile (java.io.File. idx_db))
       (with-open [idxwr (clojure.java.io/writer idx_db)]
         (map #(do (copy-one-file % (str data_dir "/" %)) (.write idxwr (str % " " (md5file %))))
           (map #(.getCanonicalPath %)
             (walk-dir locdir))))))
