;; size of chunk with which file will be chopped and checksummed
(def csum-chunk-size 1024)

;; finalize the digest by padding and return string representation
(defn digestDone [digest buf len]
  (apply str (map (partial format "%02x") (.digest digest))))

;; organize md5 o/p such that it is ordered as,
;;   [0 file-len md5] [0 chunk1 md5] [chunk1 chunk2 md5] ...
(defn md5-organized-fmt [inp]
  (cons (first inp) (reverse (rest inp))))

;; Parameter used to compute rolling-hash
;; We can chose power-of-two numbers as well for faster compute
(def rolling-hash-base 257)
(def rolling-hash-mod 2147483648)
(defn rolling-hash [s len]
  ;; It is pretty slow to do such computation in clojure, therefore setting
  ;; 'off' to 'len' instead of 0, it will always return 0 for now
  (loop [h 0 off len]
    (if (< off len)
      (recur (mod (+ (* rolling-hash-base (aget s off)) h) rolling-hash-mod) (inc off))
      h)))

(defn md5file [file]
  (with-open [input (java.io.FileInputStream. file)]
    (let [d1 (java.security.MessageDigest/getInstance "MD5")
          bufsize 262144
          buf (byte-array bufsize)
         ]
      (loop [last_cnt 0]
        (let [cnt (.read input buf 0 bufsize)]
          (if (= -1 cnt)
            (digestDone d1 buf last_cnt)
            (do
              (.update d1 buf 0 cnt)
              (recur cnt))))))))

(defn md5set [file]
  (with-open [input (java.io.FileInputStream. file)]
    (let [d1 (java.security.MessageDigest/getInstance "MD5")
          d2 (java.security.MessageDigest/getInstance "MD5")
          bufsize csum-chunk-size
          buf (byte-array bufsize)
         ]
      (loop [off 0 last_cnt 0 ent ()]
        (let [cnt (.read input buf 0 bufsize)]
          (if (= -1 cnt)
            (md5-organized-fmt (cons [0 off (digestDone d1 buf last_cnt) (rolling-hash buf cnt)] ent))
            (do
              (.reset d2)
              (.update d1 buf 0 cnt)
              (.update d2 buf 0 cnt)
              (recur (+ off cnt) cnt (cons [off cnt (digestDone d2 buf cnt) (rolling-hash buf cnt)] ent)))))))))

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

(defn idx-tbl-add [idx file dst md5]
  (.write idx (str file "|" dst "|" md5 "\n")))

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
        (let [dst (str data_dir "/" ff)]
          (copy-one-file false ff dst)
          (idx-tbl-add idxwr ff dst (md5set ff)))))))

(defn parse-idx-tbl [file]
  (with-open [r (clojure.java.io/reader file)]
    (loop [l (line-seq r)
           h (hash-map)]
      (let [s (first l)]
        (if-not s
          h
          (let [v (.split #"\|" s)]
            (recur (rest l) (assoc h (aget v 0) (aget v 2)))))))))

(defn copy-modified-files [locdir bkdir ts old-idx-db]
  (let [data_dir (normalized-path (str bkdir "/data/" ts))
        idx_db (normalized-path (str bkdir "/.metadata/index/file_index." ts))
        itbl (parse-idx-tbl old-idx-db)]
    (.createNewFile (java.io.File. idx_db))
    (with-open [new-idx (open-idx-writer idx_db)]
      (doseq [ff (map #(.getCanonicalPath %) (walk-dir locdir))]
        (let [bkmd5 (get itbl ff)
              locmd5 (md5file ff)
              dst (str data_dir "/" ff)]
          (idx-tbl-add new-idx ff dst locmd5)
          (if (not= locmd5 bkmd5)
            (copy-one-file true ff dst)))))))

(defn backup-files [locdir bkdir]
  (setup-bkdir bkdir)
  (let [cur-ts (now-seconds)
        idx-db (prev-index-db (str bkdir "/.metadata") cur-ts)]
    (if idx-db
      (copy-modified-files locdir bkdir cur-ts idx-db)
      (copy-all-files locdir bkdir cur-ts))))
