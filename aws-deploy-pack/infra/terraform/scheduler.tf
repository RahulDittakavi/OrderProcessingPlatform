# Safety net: stop the instance nightly so a forgotten box can't eat your credits.
resource "aws_iam_role" "scheduler" {
  count = var.enable_auto_stop ? 1 : 0
  name  = "${var.name_prefix}-scheduler-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "scheduler" {
  count = var.enable_auto_stop ? 1 : 0
  name  = "stop-instance"
  role  = aws_iam_role.scheduler[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ec2:StopInstances"]
      Resource = aws_instance.app.arn
    }]
  })
}

resource "aws_scheduler_schedule" "nightly_stop" {
  count                        = var.enable_auto_stop ? 1 : 0
  name                         = "${var.name_prefix}-nightly-stop"
  schedule_expression          = var.auto_stop_cron
  schedule_expression_timezone = "Asia/Kolkata"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler[0].arn
    input    = jsonencode({ InstanceIds = [aws_instance.app.id] })
  }
}
