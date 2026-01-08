$baseDir = "C:\app_dev\designated_driver\designated_customer_flutter\lib"

# core directories
New-Item -Path "$baseDir\core\constants" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\core\theme" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\core\utils" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\core\services" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\core\widgets" -ItemType Directory -Force | Out-Null

# features - auth
New-Item -Path "$baseDir\features\auth\models" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\auth\providers" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\auth\repositories" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\auth\services" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\auth\screens" -ItemType Directory -Force | Out-Null

# features - attribution
New-Item -Path "$baseDir\features\attribution\models" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\attribution\providers" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\attribution\repositories" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\attribution\services" -ItemType Directory -Force | Out-Null

# features - call
New-Item -Path "$baseDir\features\call\models" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\call\providers" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\call\repositories" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\call\services" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\call\screens" -ItemType Directory -Force | Out-Null

# features - points
New-Item -Path "$baseDir\features\points\models" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\points\providers" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\points\repositories" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\points\services" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\points\screens" -ItemType Directory -Force | Out-Null

# features - steps
New-Item -Path "$baseDir\features\steps\models" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\steps\providers" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\steps\repositories" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\steps\services" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\steps\screens" -ItemType Directory -Force | Out-Null

# features - profile
New-Item -Path "$baseDir\features\profile\models" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\profile\providers" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\profile\repositories" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\profile\screens" -ItemType Directory -Force | Out-Null

# features - games
New-Item -Path "$baseDir\features\games\screens" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\games\widgets" -ItemType Directory -Force | Out-Null

# features - home
New-Item -Path "$baseDir\features\home\providers" -ItemType Directory -Force | Out-Null
New-Item -Path "$baseDir\features\home\screens" -ItemType Directory -Force | Out-Null

# assets directories
$assetsDir = "C:\app_dev\designated_driver\designated_customer_flutter\assets"
New-Item -Path "$assetsDir\images" -ItemType Directory -Force | Out-Null
New-Item -Path "$assetsDir\icons" -ItemType Directory -Force | Out-Null
New-Item -Path "$assetsDir\logos" -ItemType Directory -Force | Out-Null

Write-Host "Directory structure created successfully!" -ForegroundColor Green
