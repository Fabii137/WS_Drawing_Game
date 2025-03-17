document.getElementById("input").addEventListener("keypress", function(event) {
    if (event.key === "Enter") {
        submitLogin();
    }
});


function submitLogin() {
    let error = document.getElementById("error");
    const name = document.getElementById("input").value.trim();
    if(name === "") {
        error.innerText = "name cannot be empty!";
        setTimeout(() => {
            error.innerText = "";
        }, 3000)
        return;
    }
    if(name.length > 20) {
        error.innerText = "name cannot be longer than 20 characters!";
        setTimeout(() => {
            error.innerText = "";
        }, 3000)
        return;
    }

    sessionStorage.setItem("username", name);
    window.location.href = 'game.html'
}