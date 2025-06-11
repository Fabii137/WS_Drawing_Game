# 🎨 Drawing Game

A real-time multiplayer drawing and guessing game implemented with **Java (backend)** and **HTML/CSS/JavaScript (frontend)** using WebSocket communication. Players take turns drawing a word while others try to guess it in the chat.

---

## 📦 Tech Stack

- **Backend:** Java using `org.java-websocket` for WebSocket support and `com.google.code.gson` for JSON parsing
- **Frontend:** Vanilla HTML, CSS, and JavaScript
- **Game Features:**
  - Real-time drawing
  - Word guessing game with hints
  - Round-based score system
  - Leaderboard tracking

---

## 🚀 Getting Started

### 🖥 Backend Setup

1. **Dependencies Required:**
   - `org.java-websocket`
   - `com.google.code.gson`

2. **Word List:**
   - You can change the `words.txt` file to update the drawing words.
   - Add one word per line.

3. **Start Backend** 


### 🌐 Frontend Setup

  **The frontend handles**:
   - Mouse events to draw on canvas
   - Sending normalized stroke data to the server
   - Receiving, rendering and sending strokes, messages
   - Receiving and rendering game status

   **Start**
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

- Modify `ROUND_DURATION`, `ROUND_COUNT`, and scoring constants in `GameSession.java` to adjust gameplay.

---


## 🙌 Acknowledgments

- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket)
- [Gson](https://github.com/google/gson)
