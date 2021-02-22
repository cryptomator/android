source "https://rubygems.org"

gem "fastlane"
gem "net-sftp"
gem "ed25519"
gem "bcrypt_pbkdf"

plugins_path = File.join(File.dirname(__FILE__), 'fastlane', 'Pluginfile')
eval_gemfile(plugins_path) if File.exist?(plugins_path)
