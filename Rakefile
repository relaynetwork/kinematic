task :swank do
  port = File.readlines(".swank.port").first
  port.chomp!
  system "lein", "swank", port
end
