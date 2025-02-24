const username = sessionStorage.getItem("username");
if(!username || username === "") {
    window.location.href = 'index.html'
}

window.addEventListener('load', () => { 
	resize();
	document.addEventListener('mousedown', startPainting); 
	document.addEventListener('mouseup', stopPainting); 
	document.addEventListener('mousemove', sketch); 
	window.addEventListener('resize', resize); 

    console.log("Page loaded. Requesting canvas...");
    socket.send(JSON.stringify({ type: "get_canvas" }));
}); 

document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
        console.log("Requesting canvas after tab switch...");
        socket.send(JSON.stringify({ type: "get_canvas" }));
    }
});

myTurn = false;
word = null;

const socket = new WebSocket(`ws://localhost:8080?username=${username}`);

socket.onopen = () => {
    console.log("Connected to server");
}

const statusElement = document.getElementById("status");

socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log(data)
    
    switch(data.type) {
        case "wait":
            statusElement.innerText = 'Status: Waiting for players';
            break;
        case "start":
            currentTurn = data.turn;
            myTurn = username === currentTurn;
            statusElement.innerText = (myTurn) ? 'Status: Your Turn!' : 'Status: Current Turn: ' + currentTurn;
            break;
        case "clear":
            clearCanvas();
            break;
        case "word":
            word = data.data;
            document.getElementById("word").innerText = `Your word is ${word}`;
            break;
        case "canvas":
            updateCanvas(data.data);
            break;
        case "nextTurn":
            word = null;
            currentTurn = data.turn;
            myTurn = username === currentTurn;
            statusElement.innerText = (myTurn) ? 'Status: Your Turn!' : 'Status: Current Turn: ' + currentTurn;
            break;
    }
};

const canvas = document.querySelector('#canvas'); 
const ctx = canvas.getContext('2d'); 

function sendCanvasData() {
    const imgData = canvas.toDataURL("image/png"); 
    socket.send(JSON.stringify({ type: "canvas", data: imgData }));
}

function updateCanvas(imgData) {
    const img = new Image();
    img.onload = function () {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0);
    };
    img.src = imgData;
}

function resize(){ 
    ctx.canvas.width = window.innerWidth / 2; 
    ctx.canvas.height = window.innerHeight / 1.7; 
} 
	
let coord = {x:0 , y:0}; 
let paint = false;
let color = 'black'; 

function getPosition(event){ 
    coord.x = event.clientX - canvas.offsetLeft; 
    coord.y = event.clientY - canvas.offsetTop; 
} 
function startPainting(event){ 
    if(myTurn) {
        paint = true; 
        getPosition(event); 
    }
} 
function stopPainting(){ 
    paint = false; 

    if(myTurn) {
        sendCanvasData();
    }
} 

function setColor(setColor) {
    color = setColor;
}

function clearCanvas() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    sendCanvasData();
}
	
function sketch(event){ 
    if (!paint) 
        return; 

    ctx.beginPath(); 
    ctx.lineWidth = 5; 
    ctx.lineCap = 'round';     
    ctx.strokeStyle = color; 
    ctx.moveTo(coord.x, coord.y); 
    getPosition(event); 
    ctx.lineTo(coord.x , coord.y); 
    ctx.stroke(); 
}


