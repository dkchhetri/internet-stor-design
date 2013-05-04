;; size of chunk with which file will be chopped and checksummed
(def csum-chunk-size 8192)

;; finalise the digest by padding and return string representation
(defn digestDone [digest buf len]
  (apply str (map (partial format "%02x") (.digest digest))))

(defn md5file [file]
  (with-open [input (java.io.FileInputStream. file)]
    (let [d1 (java.security.MessageDigest/getInstance "MD5")
          d2 (java.security.MessageDigest/getInstance "MD5")
          bufsize csum-chunk-size
          buf (byte-array bufsize)
         ]
      (loop [off 0 last_cnt 0 ent ()]
        (let [cnt (.read input buf 0 bufsize)]
          (if (= -1 cnt)
            (cons [0 off (digestDone d1 buf last_cnt)] ent)
            (do
              (.reset d2)
              (.update d1 buf 0 cnt)
              (.update d2 buf 0 cnt)
              (recur (+ off cnt) cnt (cons [off (+ off cnt) (digestDone d2 buf cnt)] ent)))))))))

(defn now-seconds []
  (int (/ (System/currentTimeMillis) 1000)))

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
