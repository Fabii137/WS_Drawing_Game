document.getElementById("input").addEventListener("keypress", function(event) {
    if (event.key === "Enter") {
        submitLogin();
    }
});


function submitLogin() {
    const name = document.getElementById("input").value.trim();
    if(name === "") {
        showError("name cannot be empty!");
        return;
    }
    if(name.length > 20) {
        showError("name cannot be longer than 20 characters!");
        return;
    }

    sessionStorage.setItem("username", name);
    window.location.href = 'game.html'
}

function showError(message) {
    const error = document.getElementById("error");
    error.innerText = message;
    setTimeout(() => {
        error.innerText = "";
    }, 3000)
}