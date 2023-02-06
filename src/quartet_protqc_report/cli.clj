(ns quartet-protqc-report.cli
  (:gen-class)
  (:require [quartet-protqc-report.task :refer [make-report!]]
            [local-fs.core :refer [file? directory? exists?]]
            [clojure.string :as clj-str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [quartet-protqc-report.version :refer [version]]))

(def cli-options
  [["-d" "--data PATH" "Data file"
    :validate [#(file? %) "Must be a valid file."]]
   ["-m" "--metadata PATH" "Metadata file"
    :validate [#(file? %) "Must be a valid file."]]
   ["-o" "--output PATH" "Data file"
    :validate [#(directory? %) "Must be a valid directory."]]
   ["-n" "--name NAME" "Report name"
    :default "report"]
   ["-D" "--description DESC" "Report Description"
    :default "Quality control report"]
   ["-v" "--version" "Show version" :default false]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Protqc - Visualizes Quality Control(QC) results for Quartet Project."
        ""
        "Usage: protqc [options]"
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the manual page for more information."]
       (clj-str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clj-str/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (:version options)
      {:exit-message (format "v%s" version)}

      (nil? (:data options))
      {:exit-message "You need to specified -d/--data argument."}

      (nil? (:metadata options))
      {:exit-message "You need to specified -m/--metadata argument."}

      (nil? (:output options))
      {:exit-message "You need to specified -o/--output argument."}

      (and (:data options) (:metadata options) (:name options) (:output options))
      {:options options}

      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "Generate ProtQC report for quartet project."
  [& args]
  (if (exists? "/opt/conda/etc/Rprofile")
    (System/setProperty "R_PROFILE_USER" "/opt/conda/etc/Rprofile")
    (System/setProperty "R_PROFILE_USER" ".env/Rprofile"))
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [result (make-report! {:data-file (:data options)
                                  :metadata-file (:metadata options)
                                  :dest-dir (:output options)
                                  :metadata {:name (:name options)
                                             :description (:description options)
                                             :plugin-name "quartet-protqc-report"
                                             :plutin-type "ReportPlugin"
                                             :plugin-version version}
                                  :task-id nil})]
        (if (= (:status result) "Success")
          (log/info (:msg result))
          (log/error (:msg result)))))
    (shutdown-agents)))
