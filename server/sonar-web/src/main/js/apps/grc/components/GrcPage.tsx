import React from "react";
import Nav from "./Nav";
import "../styles.css";

export default function GrcPage({children}) {

  return (
      <div className="grc-container">
        <header className="grc-header">
          <Nav/>
        </header>

        {children}
      </div>
  );

}
