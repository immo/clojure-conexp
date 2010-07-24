#!/bin/bash

cd $(dirname $0)

if ([ -f ./conexp-clj/bin/conexp-clj.sh ] && [ "$1" != "--rebuild" ]); then
   ./conexp-clj/bin/conexp-clj.sh --gui
else

  bin_in_home=`echo $PATH | sed 's/\:/\n/g' | grep "~/bin$"`

  if [ -n "$bin_in_home" ]; then
    echo "~/bin is in PATH"
  else
    echo "Adding ~/bin to PATH"
    PATH=~/bin:$PATH
  fi

  leiningen=`which lein`

  if [ -n "$leiningen" ]; then
    echo "leiningen found: $leiningen"
  else
    echo "leiningen not found, going to install"
    mkdir ~/bin
    wget http://github.com/technomancy/leiningen/raw/stable/bin/lein -O ~/bin/lein
    chmod +x ~/bin/lein
    ~/bin/lein self-install
  fi

  ./bundle.sh && ./conexp-clj/bin/conexp-clj.sh --gui

fi
