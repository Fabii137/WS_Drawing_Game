# 🎨 Drawing Game

A real-time multiplayer **drawing and guessing game** built with **Java (backend)** and **HTML/CSS/JavaScript (frontend)** using WebSocket communication. Players take turns drawing a word while others try to guess it in real-time.

---

## 📌 Features

- Real-time collaborative drawing
- Live guessing via chat
- Turn-based word drawing
- Hint system (reveals letters over time)
- Round-based scoring and leaderboard tracking

---

## 📦 Tech Stack

### 🖥 Backend

- **Java**
- WebSocket: [`org.java-websocket`](https://github.com/TooTallNate/Java-WebSocket)
- JSON Parsing: [`com.google.code.gson`](https://github.com/google/gson)

### 🌐 Frontend

- **Vanilla HTML/CSS/JavaScript**
- Canvas drawing
- Real-time WebSocket communication

---

## 🚀 Installation & Setup

### ✅ Prerequisites

- Java 8 or higher  
- Maven

### Backend

- Clone the Repository
  ```bash
  git clone https://github.com/Fabii137/WS_Drawing_Game.git
  cd WS_Drawing_Game/WS_Drawing_Game_Backend
  ```
- Build Project and install Dependencies
  ```bash
    mvn clean install
  ```

- Start
  ```bash
    cd target
    java -jar Drawing_Game-1.2.jar
  ```
### Frontend
- Open index.html

## 🎮 How to Play

- At least **2 players** must connect to start the game.
- Players take turns drawing a random word.
- Other players guess the word via chat.
- Points are awarded based on how quickly players guess the word.
- After a set number of rounds (default: 5), the leaderboard is shown and a new game starts.

---

## 🧠 Game Logic Highlights

- **Turn System:** Each player gets one turn per round.
- **Scoring:**
  - First correct guesses get more points.
  - Drawer earns 50% of total points guessed by others.
- **Hints:**
  - Letters are revealed at 75%, 50%, and 25% time intervals.


## 🔧 Customization
- Modidy `words.txt` to update drawing words.
- Modify `ROUND_DURATION`, `ROUND_COUNT`, and scoring constants in `GameSession.java` to adjust gameplay.

---


## 🙌 Acknowledgments

- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket)
- [Gson](https://github.com/google/gson)
