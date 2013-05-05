(defn md5file [file]
  (let [input (java.io.FileInputStream. file)
        digest (java.security.MessageDigest/getInstance "MD5")
        stream (java.security.DigestInputStream. input digest)
        bufsize (* 128 1024)
        buf (byte-array bufsize)]

  (while (not= -1 (.read stream buf 0 bufsize)))
  (apply str (map (partial format "%02x") (.digest digest)))))

(defn now-seconds []
  (int (/ (System/currentTimeMillis) 1000)))

;; return seq of File
(defn walk-dir [dir]
  (remove #(.isDirectory %)
    (file-seq (java.io.File. dir))))
;; return seq of String:file-name
(defn walk-dir2 [dir]
  (map 
    #(.getCanonicalPath %)
    (filter #(.isFile %) (file-seq (java.io.File. dir)))))

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
      (str idxdir "/index/file_index." prev-ts)
      nil)))

(defn mkdir [dir]
  (let [d (java.io.File. dir)]
    (cond
      (.isDirectory d) true
      :else (.mkdirs d))))

(defn setup-bkdir [dir]
  (let [d (java.io.File. dir)]
    (if-not (.isDirectory d)
      (do 
        (mkdir dir)
        (mkdir (str dir "/.metadata/index")))
      true)))

;; get normalized path
(defn normalized-path [path]
  (.getCanonicalPath (java.io.File. path)))

(defn open-idx-writer [idx]
  (clojure.java.io/writer idx))

(defn close-idx [idx]
  (.close idx))

(defn idx-tbl-add [idx file md5]
  (.write idx (str file "|" md5 "\n")))

;; copy one file, making directory if needed
(defn copy-one-file [verbose src dst]
  (let [parent (.getParent (java.io.File. dst))]
    (mkdir parent)
    (if verbose (println (str "Copy: " src " -> " dst)))
    (clojure.java.io/copy (clojure.java.io/file src) (clojure.java.io/file dst))))

(defn copy-all-files [locdir bkdir ts]
  (let [data_dir (normalized-path (str bkdir "/data/" ts))
        idx_db (normalized-path (str bkdir "/.metadata/index/file_index." ts))]
       (.createNewFile (java.io.File. idx_db))
       (with-open [idxwr (open-idx-writer idx_db)]
         (doseq [ff (map #(.getCanonicalPath %) (walk-dir locdir))]
           (copy-one-file false ff (str data_dir "/" ff))
           (idx-tbl-add idxwr ff (md5file ff))))))

(defn parse-idx-tbl [file]
  (with-open [r (clojure.java.io/reader file)]
    (loop [l (line-seq r)
           h (hash-map)]
      (let [s (first l)]
        (if-not s
          h
          (recur (rest l) (assoc h (first (.split #"\|" s)) (second (.split #"\|" s)))))))))

(defn copy-modified-files [locdir bkdir ts old-idx-db]
  (let [data_dir (normalized-path (str bkdir "/data/" ts))
        idx_db (normalized-path (str bkdir "/.metadata/index/file_index." ts))
        itbl (parse-idx-tbl old-idx-db)]
       (.createNewFile (java.io.File. idx_db))
       (with-open [new-idx (open-idx-writer idx_db)]
         (doseq [ff (map #(.getCanonicalPath %) (walk-dir locdir))]
           (let [bkmd5 (get itbl ff)
                 locmd5 (md5file ff)]
             (idx-tbl-add new-idx ff locmd5)
             (if (not= locmd5 bkmd5)
               (copy-one-file true ff (str data_dir "/" ff))))))))


(defn backup-files [locdir bkdir]
  (setup-bkdir bkdir)
  (let [cur-ts (now-seconds)
        idx-db (prev-index-db (str bkdir "/.metadata") cur-ts)]
    (if idx-db
      (copy-modified-files locdir bkdir cur-ts idx-db)
      (copy-all-files locdir bkdir cur-ts))))
