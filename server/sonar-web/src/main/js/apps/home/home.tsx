/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import React from "react";
import { Link } from 'react-router';
import "./home.css"

const home = () => {

    return (
        <div className="landing">
            <div className="home">
                <img className="light-emblem" src='/images/grc/CodeScanShieldEmblem.svg' alt="" />
                <h1>Welcome to CodeScan Shield</h1>
                <div className="welcome-block">
                    <div className="block" style={{ marginRight: "20px" }}>
                        <Link to="/projects">
                            <img className="grc-icon" src='/images/grc/codescan-dashboard.svg' alt="" />
                            <img className="grc-logo" src='/images/grc/codescan-logo.svg' alt="" />
                            <p>Application Security Testing</p>
                        </Link>
                    </div>
                    <div className="block">
                        <Link to="/grc">
                            <img className="grc-icon" src='/images/grc/orgscan-dashboard.svg' alt="" />
                            <img className="grc-logo" src='/images/grc/orgscan-logo.svg' alt="" />
                            <p>Policy Management</p>
                        </Link>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default home;
