$answers = @()
for ($i = 0; $i -lt 10; $i++) {
    $answers += "y"
}

$answers | & "C:\src\flutter\bin\flutter.bat" doctor --android-licenses
