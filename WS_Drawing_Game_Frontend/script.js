function submitLogin() {
    const input = document.getElementById("input").value;
    if(input === "")
        return;

    sessionStorage.setItem("username", input);
    window.location.href = 'game.html'
}