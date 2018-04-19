(ns yaml.reader-test
  (:require [clojure.test :refer :all]
            [yaml.reader :refer :all])
  (:import [org.yaml.snakeyaml.error YAMLException]))

(def multiple-docs
  "foo\n---\nbar\n...")

(def nested-hash-yaml
  "root:\n  childa: a\n  childb: \n    grandchild: \n      greatgrandchild: bar\n")

(def list-yaml
  "--- # Favorite Movies\n- Casablanca\n- North by Northwest\n- The Man Who Wasn't There")

(def hashes-lists-yaml "
items:
  - part_no:   A4786
    descrip:   Water Bucket (Filled)
    price:     1.47
    quantity:  4
  - part_no:   E1628
    descrip:   High Heeled \"Ruby\" Slippers
    price:     100.27
    quantity:  1
    owners:
      - Dorthy
      - Wicked Witch of the East
")

(def inline-list-yaml "
--- # Shopping list
[milk, pumpkin pie, eggs, juice]
")

(def inline-hash-yaml
  "{name: John Smith, age: 33}")

(def list-of-hashes-yaml "
- {name: John Smith, age: 33}
- name: Mary Smith
  age: 27
")

(def hashes-of-lists-yaml "
men: [John Smith, Bill Jones]
women:
  - Mary Smith
  - Susan Williams
")

(def typed-data-yaml "
the-bin: !!binary 0101")

(def set-yaml "
--- !!set
? Mark McGwire
? Sammy Sosa
? Ken Griff")

(def emojis-yaml
  ;; Unicode SMILING FACE WITH OPEN MOUTH AND SMILING EYES
  (apply str (Character/toChars 128516)))

(def custom-tags-yaml
  "--- !ruby/hash:ActiveSupport::HashWithIndifferentAccess
  en: TEXT IN ENGLISH
  de: TEXT IN DEUTSCH
  list: !CustomList
    - foo
    - bar
    - baz
  number: !CustomScalar 1234
  string: !CustomScalar custom-string
  date:   !Date custom-string
  expires_at: !ruby/object:DateTime 2017-05-09 05:27:43.000000000")

(deftest parse-multiple-documents
  (testing "should handle multiple yaml documents"
    (is (= ["foo" "bar"]
           (parse-documents multiple-docs)))))

(deftest parse-hash
  (let [parsed (parse-string "foo: bar")]
    (is (= "bar" (parsed :foo)))))

(deftest parse-nested-hash
  (let [parsed (parse-string nested-hash-yaml)]
    (is (= "a"   ((parsed :root) :childa)))
    (is (= "bar" ((((parsed :root) :childb) :grandchild) :greatgrandchild)))))

(deftest parse-list
  (let [parsed (parse-string list-yaml)]
    (is (= "Casablanca"               (first parsed)))
    (is (= "North by Northwest"       (nth parsed 1)))
    (is (= "The Man Who Wasn't There" (nth parsed 2)))))

(deftest parse-nested-hash-and-list
  (let [parsed (parse-string hashes-lists-yaml)]
    (is (= "A4786"  ((first (parsed :items)) :part_no)))
    (is (= "Dorthy" (first ((nth (parsed :items) 1) :owners))))))

(deftest parse-inline-list
  (let [parsed (parse-string inline-list-yaml)]
    (is (= "milk"        (first parsed)))
    (is (= "pumpkin pie" (nth   parsed 1)))
    (is (= "eggs"        (nth   parsed 2)))
    (is (= "juice"       (last  parsed)))))

(deftest parse-inline-hash
  (let [parsed (parse-string inline-hash-yaml)]
    (is (= "John Smith" (parsed :name)))
    (is (= 33           (parsed :age)))))

(deftest parse-list-of-hashes
  (let [parsed (parse-string list-of-hashes-yaml)]
    (is (= "John Smith" ((first parsed) :name)))
    (is (= 33           ((first parsed) :age)))
    (is (= "Mary Smith" ((nth parsed 1) :name)))
    (is (= 27           ((nth parsed 1) :age)))))

(deftest hashes-of-lists
  (let [parsed (parse-string hashes-of-lists-yaml)]
    (is (= "John Smith"     (first (parsed :men))))
    (is (= "Bill Jones"     (last  (parsed :men))))
    (is (= "Mary Smith"     (first (parsed :women))))
    (is (= "Susan Williams" (last  (parsed :women))))))

(deftest h-set
  (is (= #{"Mark McGwire" "Ken Griff" "Sammy Sosa"}
         (parse-string set-yaml))))

(deftest typed-data
  (let [parsed (parse-string typed-data-yaml)]
    (is (= (Class/forName "[B") (type (:the-bin parsed))))))

(deftest keywordized
  (is  (= "items" (-> hashes-lists-yaml (parse-string :keywords false) ffirst)))
  (binding [*keywordize* false]
    (is  (= "items" (-> hashes-lists-yaml parse-string ffirst))))

  (testing "custom keywordize function"
    (binding [*keywordize* #(str % "-extra")]
      (let [obj (parse-string hashes-lists-yaml)]
        (is (= ["items-extra"] (keys obj)))
        (is (= ["part_no-extra" "descrip-extra" "price-extra" "quantity-extra"]
               (-> obj (get "items-extra") first keys)))))))

(deftest emojis
  (is (pos? (count (parse-string emojis-yaml)))))

(deftest unknown-tags
  (testing "with the regular old parser "
    (is (thrown-with-msg? YAMLException #"Invalid tag: !ruby/hash:ActiveSupport::HashWithIndifferentAccess"
          (parse-string custom-tags-yaml))))
  (testing "with the passthrough-constructor"
    (is (= {:en "TEXT IN ENGLISH"
            :de "TEXT IN DEUTSCH"
            :list ["foo" "bar" "baz"]
            :number "1234"              ;NOTE: tagged numbers are interpreted as strings
            :string "custom-string"
            :date "custom-string"
            :expires_at "2017-05-09 05:27:43.000000000"}
           (parse-string custom-tags-yaml :constructor passthrough-constructor)))))
