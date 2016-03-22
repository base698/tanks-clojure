(ns tanks.core
  (:require
    [reagent.core :as r]
    [tanks.utils :refer [log]]))

(enable-console-print!)

(def WIDTH 800)
(def HEIGHT 600)

(defonce world { 
  :width WIDTH
  :height HEIGHT
})

(def entity-id 1)

(defn get-id []
  (set! entity-id (inc entity-id)) entity-id)

(defonce first-id (get-id))

(defn get-player [p c]
  {:id (get-id)
   :type :player
   :score 0
   :color c
   :health 100
   :position p
   :w 20
   :h 20
   :angle 0
   :last-shot 0
   :bombs 1})

(def player (get-player [200, 200] "#072"))

(defonce keypresses (atom {}))

(def entities (atom {(player :id) player
                     first-id
                     {:id first-id
                      :type :ant
                      :w 10
                      :h 10
                      :health 10
                      :position [400, 400]}}))

(defn get-shots [entities]
  (filter #(= :shot (%1 :type)) (vals entities)))

(defn get-players-and-walls [entities]
  (remove #(= :shot (%1 :type)) (vals entities)))

(defn within? [p q]
  (let [[px1 py1] (p :position)
        {pw :w ph :h} p
        px2 (+ px1 pw)
        py2 (+ py1 ph)
        [qx1 qy1] (q :position)
        {qw :w qh :h} q
        qx2 (+ qx1 qw)
        qy2 (+ qy1 qh)]
    (and (< px1 qx2)
         (> px2 qx1)
         (< py1 qy2)
         (> py2 qy1))))
 
(defonce key-map { 87 :w 
                   83 :s 
                   65 :a 
                   68 :d 
                   32 :space})

(defn add-ant []
  (let [id (get-id)]
    (swap! entities 
      assoc 
      id {:id id
          :type :ant
          :w 10
          :h 10
          :health 10
          :position [(rand-int 400) (rand-int 400)]})))


;; todo finish
(defn add-player []
  (let [player (get-player [500 500] "#622")
        id (player :id)]
    (swap! entities assoc id player)))


(defonce player-key-map {:space {:type :shoot} 
                         :b {:type :player-bomb} 
                         :w {:type :player-move :player-move 1.1} 
                         :s {:type :player-move :player-move -1.1} 
                         :a {:type :player-turn :player-turn -0.1} 
                         :d {:type :player-turn :player-turn 0.1}})

(defn ease-in-quad [x t b c d]
    (+ (* c (/ t d) (/ t d)) b))

(defn get-ctx [] 
  (let [canvas (.getElementById js/document "screen")
        ctx (.getContext canvas "2d")]
  ctx))

(defmulti draw-entity :type)

(defmethod draw-entity :shrapnel [p])

(defmethod draw-entity :player [p]
  (let [ctx (get-ctx)
        position (p :position)
        x (first position)
        y (second position)
        w (p :w)
        h (p :h)]
    (.save ctx)
    (set! (.-fillStyle ctx) (p :color))
    (.translate ctx (+ x  (/ w 2)) (+ y (/ h 2)))
    (.rotate ctx (p :angle))
    (.fillRect ctx -10 -10 w h)
    (.fillRect ctx 8 -2 5 5)
    (.restore ctx)))

(defmethod draw-entity :shot [e]
  (let [ctx (get-ctx)
        position (e :position)
        [x y] position
        w (e :w)
        h (e :h)]
    (.fillRect ctx x y w h)))

(defmethod draw-entity :ant [p]
  (let [ctx (get-ctx) position (p :position)]
    (.fillRect ctx (first position) (last position) (p :w) (p :h))))

(defmethod draw-entity :wall [p])

(defn player-field! [field value]
  (swap! player assoc field value))

(defn player-position! [pos]
  (player-field! :position pos))

(def PI (.-PI js/Math))
(defn sin [a] (.sin js/Math a))
(defn atan [a] (.atan js/Math a))
(defn atan2 [a] (.atan2 js/Math a))
(defn cos [a] (.cos js/Math a))

(defn in-bounds? [x y] 
  (and (>= x -10) (< x WIDTH) (>= y -10) (< y HEIGHT)))

(defn can-shoot? [p t]
  (> (- t (p :last-shot)) 750))

(defn find-first [pred coll]
  (first (filter pred coll)))

(defn between? [a b]
   (and (>= a b) (<= a (+ b 5))))

(defn oob? [p]
  (let [[x y] p]
    (or (> x WIDTH)
        (> y HEIGHT)
        (< x 0)
        (< y 0))))

(defmulti do-event (fn [e]
                     (:type e)))

(defmethod do-event :default [x])

;; (defmethod do-event :ant-move [e]
;;   (let [id (e :id) 
;;         ant (@entities id) 
;;         deltaY (- (get-y ant) (get-y @player))
;;         deltaX (- (get-x ant) (get-x @player))
;;         angle (* (/ 180 PI) (atan (/ deltaY deltaX)))
;;         position (ant :position)
;;         x (+ (first position) (* (cos angle) 1 ))
;;         y (+ (second position) (* (sin angle) 1 ))]
;;     (swap! entities assoc-in [id :position] [x y])))

(defmethod do-event :shot-move [e]
    (let [move (e :shot-move)
          shot (@entities (e :id))
          position (shot :position)
          [x y w h] position
          x (+ x (* (cos (shot :angle)) move ))
          y (+ y (* (sin (shot :angle)) move ))]
          (swap! entities assoc-in [(e :id) :position] [x y w h])))

(defmethod do-event :player-turn [e]
  (let [player (e :player)
        pid (player :id)
        angle (e :player-turn)]
    (swap! entities assoc-in [pid :angle]
           (+ angle (player :angle)))))

(swap! entities assoc-in [3 :angle] 83)

(defmethod do-event :damage [e]
  (let [id (get-in e [:entity :id])
        val (e :val)]
    (swap! entities update-in [id :health] #(- %1 val))
    (if (<= (get-in @entities [id :health]) 0)
      {:type :death :entity (@entities id)}
      nil)))

;; TODO: better death handling
(defmethod do-event :death [e]
  (swap! entities dissoc (get-in e [:entity :id]))
  (prn e))

(defmethod do-event :hit [e]
  (let [{shot-id :shot-id
         from-player-id :from-player
         hit-entity :to} e]
    (swap! entities update-in
           [from-player-id :score] (partial + 5))
    (swap! entities dissoc shot-id)
    {:type :damage :val 5 :entity hit-entity}))

;; oob, other player hit
(defn legal? [player new-position]
  (let [p (assoc player :position new-position)
        pid (player :id)
        others (get-players-and-walls @entities)]
    (and (not (oob? new-position))
         (not-any? #(within? p %1) (remove #(= (:id %1) pid) others)))))

(defmethod do-event :player-move [e]
  (let [p (e :player)
        old-position (p :position) 
        move (e :player-move)
        x (* (cos (p :angle)) move )
        y (* (sin (p :angle)) move )
        arr [[x y] old-position]
        new-position [(+ x (first old-position)) (+ y (second old-position))]]
    (if (legal? p new-position)
       (swap! entities assoc-in [(p :id) :position] new-position))))

(defmethod do-event :shoot [e]
  (let [t (e :timestamp)
        player (:player e)
        id (:id player)
        x (get-in player [:position 0])
        y (get-in player [:position 1])]
    (when (can-shoot? player t) 
      (let [id (get-id)]
        (swap! entities assoc id {
          :id id 
          :type :shot 
          :from-player (player :id)
          :angle (player :angle) 
          :w 4
          :h 4
          :position [(+ 7 x) (+ 7 y)]}))
      (swap! entities assoc-in [id :last-shot] t))))

(defn draw-world []
  (let [ctx (get-ctx)]
    (.clearRect ctx 0 0 800 600)
      (doseq [x (vals @entities)]
          (do (draw-entity x)))))

; these are events that just happen 
(defn get-world-events [timestamp] 
  (map (fn [ent]
         ({:shot {:type :shot-move :shot-move 3
                  :id (ent :id)}
           ; :ant {:ant-move 3
           ; :id (ent :id)}
   } (ent :type))) (vals @entities)))

(defn handle-events [events] 
  (if (empty? events) nil
      (handle-events
       (keep do-event events))))

    
;; lame hit-detection
(defn detect-hits []
  (mapcat (fn [s]
            (keep #(if (do (and (not= (%1 :id) (get-in s [:from-player]))
                            (within? s %1)))
                     {:type :hit
                      :shot-id (s :id)
                      :from-player (s :from-player)
                      :to %1}
                    nil)
                 (get-players-and-walls @entities))
            ) (get-shots @entities)))

(defn detect-shot-oob [shots]
  (let [oob (filter #(oob? (%1 :position)) shots)]
    (apply (partial swap! entities dissoc)
           (map :id oob))))        


(defn update-world [keypresses timestamp]
  (let [press-list (seq
                    (select-keys
                     keypresses
                     (for [[k v] keypresses :when v] k)))
        player-events (->> press-list
                           (map #(player-key-map (first %1)))
                           (map #(assoc %1
                                        :timestamp timestamp
                                        :player (@entities (player :id)))))
        world-events (get-world-events timestamp)]
    (do 
        (handle-events player-events)
        (handle-events world-events)
        (handle-events (detect-hits)) ; (get-shots @entities) (get-entities @entities))
        (detect-shot-oob (get-shots @entities)))))

(defn start []
   ;(.setInterval js/window add-ant 8000)
   (println "start called")
   ((fn render-loop [timestamp]
       (update-world @keypresses timestamp)
       (draw-world)
       (.requestAnimationFrame js/window render-loop))))

(defonce main
  (do
    ;(.addEventListener js/window "click")
    (.addEventListener (.getElementById js/document "start")
                       "click" (comp (fn [a]
                                       (.blur (.getElementById js/document "start")))
                                     start) )
    (.addEventListener js/window "keydown"
       #(do (swap! keypresses
               assoc
               (key-map (aget %1 "keyCode")) true) false))
    (.addEventListener js/window "keyup"
       #(swap! keypresses
               assoc
               (key-map (aget %1 "keyCode")) false))))

